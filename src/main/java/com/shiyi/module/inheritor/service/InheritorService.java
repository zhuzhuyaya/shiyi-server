package com.shiyi.module.inheritor.service;

import com.shiyi.module.inheritor.dto.InheritorApplyDTO;
import com.shiyi.module.inheritor.entity.Inheritor;

import java.util.List;

public interface InheritorService {
    void apply(InheritorApplyDTO dto);
    Inheritor currentInheritor();
    List<Inheritor> listByStatus(Integer status);
    void verify(Long inheritorId, boolean pass);
}
