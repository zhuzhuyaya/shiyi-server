# -*- coding: utf-8 -*-
# 全链路接口测试：认证 -> 权限 -> 传承人入驻 -> 发课/场次/封面 -> 管理员审核 -> C端门户 -> 越权防护
import json, base64, io, sys, pymysql
import urllib.request, urllib.error

BASE = "http://localhost:8080/api/v1"
OUT = []
def log(tag, content):
    OUT.append(f"[{tag}] {content}")

def call(method, path, token=None, body=None, raw_body=None, headers=None, expect_http=None):
    url = BASE + path
    h = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        h["Content-Type"] = "application/json"
    if raw_body is not None:
        data = raw_body
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        resp = urllib.request.urlopen(req, timeout=10)
        http = resp.status
        payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        http = e.code
        try: payload = json.loads(e.read().decode("utf-8"))
        except Exception: payload = {"raw": "?"}
    return http, payload

# 1x1 红色 PNG
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFBgI/LnNSvQAAAABJRU5ErkJggg==")

def multipart(field, filename, content, ctype="image/png"):
    buf = io.BytesIO()
    boundary = "----testboundary123"
    buf.write(f"--{boundary}\r\n".encode())
    buf.write(f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'.encode())
    buf.write(f"Content-Type: {ctype}\r\n\r\n".encode())
    buf.write(content)
    buf.write(f"\r\n--{boundary}--\r\n".encode())
    return buf.getvalue(), {"Content-Type": f"multipart/form-data; boundary={boundary}"}

def step(name, http, payload, expect_code=0, note=""):
    ok = payload.get("code") == expect_code
    tag = "PASS" if ok else "FAIL"
    log(f"{tag} {name}", f"http={http} code={payload.get('code')} msg={payload.get('msg')} {note}")
    return ok

# ---------- 一、认证 ----------
import random
PA = "139%08d" % random.randint(0, 99999999)   # 传承人甲
PB = "137%08d" % random.randint(0, 99999999)   # 管理员乙
PC = "136%08d" % random.randint(0, 99999999)   # 传承人丙（越权+驳回用）

log("INFO", f"测试账号 A={PA} B={PB} C={PC}")

step("注册A", *call("POST", "/auth/register", body={"phone": PA, "password": "test123", "nickname": "测试传承人甲"}))
step("重复注册A", *call("POST", "/auth/register", body={"phone": PA, "password": "test123", "nickname": "重复"}), expect_code=400)
step("注册B", *call("POST", "/auth/register", body={"phone": PB, "password": "test123", "nickname": "测试管理员乙"}))
step("注册C", *call("POST", "/auth/register", body={"phone": PC, "password": "test123", "nickname": "测试传承人丙"}))
step("错误密码登录", *call("POST", "/auth/login", body={"phone": PA, "password": "wrong!"}), expect_code=400)
step("未注册登录", *call("POST", "/auth/login", body={"phone": "13000000000", "password": "test123"}), expect_code=400)

h, p = call("POST", "/auth/login", body={"phone": PA, "password": "test123"})
step("登录A", h, p); tokA = p["data"]["tokenValue"] if p.get("data") else ""
h, p = call("POST", "/auth/login", body={"phone": PB, "password": "test123"})
step("登录B", h, p); tokB = p["data"]["tokenValue"] if p.get("data") else ""
h, p = call("POST", "/auth/login", body={"phone": PC, "password": "test123"})
step("登录C", h, p); tokC = p["data"]["tokenValue"] if p.get("data") else ""

h, p = call("GET", "/auth/me", token=tokA)
step("A查询个人信息", h, p, note=str((p.get("data") or {}).get("role")))

# ---------- 二、权限边界 ----------
h, p = call("GET", "/auth/me")
step("无token访问me", h, p, expect_code=401)
h, p = call("GET", "/admin/inheritors", token=tokA)
step("普通用户访问admin接口", h, p, expect_code=403)
h, p = call("POST", "/inheritor/courses", token=tokA,
            body={"heritageItemId": 2, "title": "未授权发课", "address": "x", "lng": 100.1, "lat": 25.6})
step("USER角色直接发课", h, p, expect_code=403)

# ---------- 三、传承人入驻 ----------
h, p = call("GET", "/inheritor/me", token=tokA)
step("未入驻查档案", h, p, expect_code=400)
h, p = call("POST", "/inheritor/apply", token=tokA,
            body={"name": "测试甲扎染工坊", "heritageItemId": 2, "bio": "白族扎染传承", "contact": "wx-test-A"})
step("A提交入驻申请", h, p)
h, p = call("GET", "/auth/me", token=tokA)
log("INFO", "A申请后角色 = " + str((p.get("data") or {}).get("role")))
h, p = call("POST", "/inheritor/apply", token=tokA,
            body={"name": "重复申请", "heritageItemId": 2, "bio": "", "contact": "x"})
step("A重复申请", h, p, expect_code=400)
h, p = call("GET", "/inheritor/me", token=tokA)
step("A查自己档案", h, p, note="inheritorId=" + str(p.get("data") or {}.get("id")))

# ---------- 四、发课 / 场次 / 封面 ----------
course_body = {"heritageItemId": 2, "title": "全链路测试扎染课", "address": "云南省大理市人民路88号",
               "lng": 100.156, "lat": 25.693, "description": "接口测试创建", "tags": "测试,扎染"}
h, p = call("POST", "/inheritor/courses", token=tokA, body=course_body)
step("A发布课程", h, p)
h, p = call("POST", "/inheritor/courses", token=tokA, body={"heritageItemId": 2, "title": "缺地址", "lng": 1, "lat": 1})
step("发布课程缺地址", h, p, expect_code=400)

h, p = call("GET", "/inheritor/courses", token=tokA)
courses = p.get("data") or []
my_course = next((c for c in courses if c["title"] == "全链路测试扎染课"), None)
log("INFO", f"A我的课程数={len(courses)} 测试课id={my_course and my_course['id']} status={my_course and my_course['status']}")
cid = my_course["id"] if my_course else 0

h, p = call("POST", f"/inheritor/courses/{cid}/sessions", token=tokA,
            body={"startTime": "2026-11-20T10:00:00", "durationMin": 120, "price": 99.5, "capacity": 20})
step("添加场次(未来)", h, p)
h, p = call("POST", f"/inheritor/courses/{cid}/sessions", token=tokA,
            body={"startTime": "2020-01-01T10:00:00", "durationMin": 120, "price": 99.5, "capacity": 20})
step("添加场次(过去时间)", h, p, expect_code=400)
h, p = call("POST", f"/inheritor/courses/{cid}/sessions", token=tokA,
            body={"startTime": "2026-11-21T10:00:00", "durationMin": 10, "price": -5, "capacity": 0})
step("添加场次(非法时长/价格/名额)", h, p, expect_code=400)
h, p = call("GET", f"/inheritor/courses/{cid}/sessions", token=tokA)
step("查场次列表", h, p, note=f"count={len(p.get('data') or [])}")

body, hdrs = multipart("file", "cover.png", PNG)
h, p = call("POST", f"/inheritor/courses/{cid}/cover", token=tokA, raw_body=body, headers=hdrs)
step("上传课程封面", h, p)
cover_url = p.get("data") or ""
if cover_url.startswith("http"):
    try:
        r = urllib.request.urlopen(cover_url, timeout=10)
        log("PASS 封面URL可访问", f"{cover_url} -> {r.status} {r.headers.get('Content-Type')}")
    except Exception as e:
        log("FAIL 封面URL可访问", str(e))

# ---------- 五、C 升级传承人后横向越权 ----------
call("POST", "/inheritor/apply", token=tokC, body={"name": "测试丙工坊", "heritageItemId": 3, "bio": "", "contact": "wx-C"})
h, p = call("POST", f"/inheritor/courses/{cid}/sessions", token=tokC,
            body={"startTime": "2026-11-20T14:00:00", "durationMin": 60, "price": 10, "capacity": 5})
step("C给A的课程加场次(横向越权)", h, p, expect_code=400)
h, p = call("GET", f"/inheritor/courses/{cid}/sessions", token=tokC)
step("C查看A的场次(横向越权)", h, p, expect_code=400)
body, hdrs = multipart("file", "hack.png", PNG)
h, p = call("POST", f"/inheritor/courses/{cid}/cover", token=tokC, raw_body=body, headers=hdrs)
step("C改A的课程封面(横向越权)", h, p, expect_code=400)

# ---------- 六、管理员审核 ----------
# B 升级为 ADMIN（测试便利，直接改库）
conn = pymysql.connect(host="localhost", user="root", password="123456", database="shiyi")
cur = conn.cursor()
cur.execute("update user set role='ADMIN' where phone=%s", (PB,))
conn.commit()
cur.execute("select id, name, verify_status, user_id from inheritor where name like %s", ("测试%",))
log("INFO", "待审传承人 = " + json.dumps(cur.fetchall(), ensure_ascii=False, default=str))
cur.execute("select id, title, status, inheritor_id from course where title like %s", ("%全链路%",))
log("INFO", "待审课程 = " + json.dumps(cur.fetchall(), ensure_ascii=False, default=str))
conn.close()

h, p = call("GET", "/admin/inheritors?status=0", token=tokB)
pending = p.get("data") or []
log("INFO", f"待审传承人数={len(pending)}")
inheritor_a = next((i for i in pending if i["name"] == "测试甲扎染工坊"), None)
inheritor_c = next((i for i in pending if i["name"] == "测试丙工坊"), None)
if inheritor_a:
    h, p = call("POST", f"/admin/inheritors/{inheritor_a['id']}/verify?pass=true", token=tokB)
    step("审核A传承人通过", h, p)
if inheritor_c:
    h, p = call("POST", f"/admin/inheritors/{inheritor_c['id']}/verify?pass=false", token=tokB)
    step("审核C传承人驳回", h, p)
h, p = call("GET", "/admin/courses?status=0", token=tokB)
pending_c = p.get("data") or []
mine = next((c for c in pending_c if c["id"] == cid), None)
log("INFO", f"待审课程数={len(pending_c)} 测试课在内={mine is not None}")
if mine:
    h, p = call("POST", f"/admin/courses/{cid}/verify?pass=true", token=tokB)
    step("审核课程通过", h, p)
    h, p = call("POST", f"/admin/courses/{cid}/verify?pass=true", token=tokA)
    step("非管理员审核课程", h, p, expect_code=403)

# ---------- 七、C 端门户 ----------
h, p = call("GET", "/courses?keyword=" + urllib.parse.quote("全链路"))
recs = (p.get("data") or {}).get("records") or []
step("C端搜索新课", h, p, note=f"命中={len(recs)}")
h, p = call("GET", f"/courses/{cid}")
d = p.get("data") or {}
step("C端课程详情", h, p, note=f"cover={str(d.get('cover'))[:50]} inheritorName={d.get('inheritorName')} sessions={len(d.get('sessions') or [])}")
h, p = call("GET", "/courses/hot")
log("INFO", f"热门榜条数={len(p.get('data') or [])}")
h, p = call("GET", "/courses/999999")
step("C端查不存在课程", h, p, expect_code=400)

# ---------- 八、通用文件上传 ----------
body, hdrs = multipart("file", "test.png", PNG)
h, p = call("POST", "/files/upload", token=tokA, raw_body=body, headers=hdrs)
step("通用文件上传", h, p, note=str(p.get('data'))[:60])

with open("test-results.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(OUT))
print("done, lines:", len(OUT))
