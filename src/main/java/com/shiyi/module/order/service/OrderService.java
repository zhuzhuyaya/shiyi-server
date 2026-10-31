package com.shiyi.module.order.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.common.BusinessException;
import com.shiyi.module.course.entity.Course;
import com.shiyi.module.course.entity.CourseSession;
import com.shiyi.module.course.mapper.CourseMapper;
import com.shiyi.module.course.mapper.CourseSessionMapper;
import com.shiyi.module.inheritor.entity.Inheritor;
import com.shiyi.module.inheritor.service.InheritorService;
import com.shiyi.module.order.entity.Order;
import com.shiyi.module.order.mapper.OrderMapper;
import com.shiyi.module.order.vo.OrderVO;
import com.shiyi.module.search.mq.CourseSyncEvent;
import com.shiyi.module.search.mq.RabbitMQConfig;
import com.shiyi.module.seckill.service.SeckillService;
import com.shiyi.module.user.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final CourseMapper courseMapper;
    private final CourseSessionMapper courseSessionMapper;
    private final InheritorService inheritorService;
    private final MessageService messageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;

    /** 我的订单列表:批量组装,复用第 5 课的 N+1 解法 */
    public List<OrderVO> listMine() {
        long userId = StpUtil.getLoginIdAsLong();
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> titles = courseMapper.selectBatchIds(
                        orders.stream().map(Order::getCourseId).distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Course::getTitle));
        Map<Long, CourseSession> sessions = courseSessionMapper.selectBatchIds(
                        orders.stream().map(Order::getSessionId).distinct().toList()).stream()
                .collect(Collectors.toMap(CourseSession::getId, s -> s));

        return orders.stream().map(o -> {
            OrderVO vo = new OrderVO();
            vo.setId(o.getId());
            vo.setOrderNo(o.getOrderNo());
            vo.setCourseTitle(titles.get(o.getCourseId()));
            CourseSession s = sessions.get(o.getSessionId());
            if (s != null) {
                vo.setStartTime(s.getStartTime());
            }
            vo.setAmount(o.getAmount());
            vo.setStatus(o.getStatus());
            vo.setCheckinCode(o.getCheckinCode());
            vo.setCreateTime(o.getCreateTime());
            return vo;
        }).toList();
    }

    /** 支付完成唯一落库口:条件更新 + 核销码 + 站内信。模拟支付/支付宝回调/查单同步都汇到这里 */
    public void markPaid(Order order) {
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, Order.STATUS_PENDING_PAY)   // 状态机守卫,天然幂等
                .set(Order::getStatus, Order.STATUS_PAID)
                .set(Order::getPayTime, LocalDateTime.now())
                .set(Order::getCheckinCode, RandomUtil.randomString(8)));
        if (rows == 0) {
            throw new BusinessException("订单当前不可支付(已支付或已超时关闭)");
        }
        messageService.send(order.getUserId(), "支付成功",
                "订单 " + order.getOrderNo() + " 已支付,核销码已生成,到店出示即可");
    }

    /** 模拟支付(压测/演示用,保留):JMeter 直接打这个,不走支付宝 */
    public void pay(Long orderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");   // 属主:防横向越权,统一话术
        }
        markPaid(order);
    }

    /** 延迟关单:超时未付 → 取消 + 双写回补。与 pay 抢同一个状态位,条件更新定输赢 */
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;   // 数据异常:正常返回,别让消息无限重投
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, Order.STATUS_PENDING_PAY)   // 抢状态位
                .set(Order::getStatus, Order.STATUS_CANCELLED));
        if (rows == 0) {
            log.info("订单 {} 已支付或已关闭,关单幂等退出", orderId);
            return;
        }
        courseSessionMapper.restoreStock(order.getSessionId());                 // DB 回补
        stringRedisTemplate.opsForValue()
                .increment(SeckillService.KEY_STOCK + order.getSessionId());    // Redis 回补
        stringRedisTemplate.delete("cache:course:detail:" + order.getCourseId());
        // buyer 集合刻意不移除:关单后不允许再抢同一场——防"占坑不放"薅羊毛,uk 物理上也拦住了
        messageService.send(order.getUserId(), "订单已取消",
                "订单 " + order.getOrderNo() + " 超时未支付已关闭,名额已释放给其他用户");
    }

    /** 到店核销:PAID → CHECKED_IN。属主传导 + 销量 +1 + 一致性动作 */
    @Transactional(rollbackFor = Exception.class)
    public void checkin(String code) {
        Inheritor inheritor = inheritorService.currentInheritor();
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getCheckinCode, code));
        if (order == null) {
            throw new BusinessException("核销码无效");
        }
        // 属主传导:订单 → 场次 → 课程 → 传承人。不是我的课,统一"核销码无效"防探测
        CourseSession session = courseSessionMapper.selectById(order.getSessionId());
        Course course = courseMapper.selectById(session.getCourseId());
        if (!course.getInheritorId().equals(inheritor.getId())) {
            throw new BusinessException("核销码无效");
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, Order.STATUS_PAID)
                .set(Order::getStatus, Order.STATUS_CHECKED_IN));
        if (rows == 0) {
            throw new BusinessException("该订单当前不可核销(未支付或已核销)");
        }
        courseMapper.increaseSales(course.getId());
        // 一致性动作(同第 6 课 notifyCourseChanged):销量变了,详情缓存和 ES 文档都要跟着变
        stringRedisTemplate.delete("cache:course:detail:" + course.getId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_COURSE, "",
                new CourseSyncEvent(course.getId()));
        messageService.send(order.getUserId(), "核销成功",
                "课程《" + course.getTitle() + "》核销成功,期待你的评价");
    }

    /** 退款:PAID → REFUNDED。开场前退 → 回补库存;开场后退 → 名额作废 */
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long orderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");   // 属主:与 pay 统一话术
        }
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, Order.STATUS_PAID)          // 只有已支付能退
                .set(Order::getStatus, Order.STATUS_REFUNDED));
        if (rows == 0) {
            throw new BusinessException("订单当前不可退款(未支付、已核销或已退款)");
        }

        CourseSession session = courseSessionMapper.selectById(order.getSessionId());
        boolean beforeStart = session != null
                && LocalDateTime.now().isBefore(session.getStartTime());
        if (beforeStart) {
            courseSessionMapper.restoreStock(session.getId());               // DB 回补
            stringRedisTemplate.opsForValue()
                    .increment(SeckillService.KEY_STOCK + session.getId());  // Redis 回补
            stringRedisTemplate.delete("cache:course:detail:" + order.getCourseId());
            log.info("订单 {} 开场前退款,场次 {} 库存已回补", orderId, session.getId());
        } else {
            log.info("订单 {} 开场后退款,名额作废不回补", orderId);
        }
        // buyer 集合刻意不移除 + uk_user_session 仍在:退过款也不能再抢同一场,防刷
        messageService.send(order.getUserId(), "退款成功",
                "订单 " + order.getOrderNo() + " 已退款 ¥" + order.getAmount()
                        + (beforeStart ? ",名额已释放" : ",课程已开场,名额不再释放"));
    }

    /** 订单详情:属主 + 组装方式同 listMine */
    public OrderVO detail(Long orderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        Course course = courseMapper.selectById(order.getCourseId());
        vo.setCourseTitle(course == null ? null : course.getTitle());
        CourseSession s = courseSessionMapper.selectById(order.getSessionId());
        if (s != null) {
            vo.setStartTime(s.getStartTime());
        }
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setCheckinCode(order.getCheckinCode());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}