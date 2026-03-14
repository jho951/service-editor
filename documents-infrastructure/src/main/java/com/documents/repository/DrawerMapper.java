package com.documents.repository;

import com.documents.domain.Drawer;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DrawerMapper {
    int insert(Drawer drawer);
    Drawer findById(@Param("id") UUID id);
    int updatePartial(Drawer drawer);
    int delete(@Param("id") UUID id);
    List<Drawer> findPage(@Param("offset") int offset, @Param("limit") int limit);
}
