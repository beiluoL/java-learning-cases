package com.learn.mybatismini.demo.mapper;

import com.learn.mybatismini.annotations.Param;
import com.learn.mybatismini.demo.entity.User;

import java.util.List;

/**
 * 用户 Mapper 接口（XML 驱动）。
 *
 * <h3>注意 namespace 必须与本接口全限定名一致</h3>
 * {@code UserMapper.xml} 里的 {@code <mapper namespace="com.learn.mybatismini.demo.mapper.UserMapper">}
 * 正是靠这个约定，把 XML 里的 {@code <select id="selectById">} 绑定到本接口的 {@link #selectById} 方法。
 *
 * <p>本接口刻意覆盖多种场景，方便在 MainApplication 里逐一演示：
 * 基础查询、动态 where/if、foreach、choose/when、insert 主键回填、update 动态 set、
 * 删除、@Param 多参计数、${} 排序（注意注入风险）。
 */
public interface UserMapper {

    /** 按主键查询（单参数、无 @Param，靠 -parameters 取真实名 id） */
    User selectById(Long id);

    /** 查询全部 */
    List<User> selectAll();

    /**
     * 动态条件查询：用户名模糊、年龄精确、邮箱模糊 —— 三个条件可任意组合。
     * 用 @Param 让 SQL 里 #{userName} / #{age} / #{emailLike} 能稳定取到值。
     */
    List<User> selectByCondition(@Param("userName") String userName,
                                 @Param("age") Integer age,
                                 @Param("emailLike") String emailLike);

    /** foreach 批量查询：WHERE id IN (?, ?, ?) */
    List<User> selectByIds(@Param("ids") List<Long> ids);

    /** choose/when/otherwise 分支查询 */
    List<User> selectByChoose(@Param("userName") String userName, @Param("email") String email);

    /** 插入并回填自增主键（XML 里 useGeneratedKeys="true" keyProperty="id"） */
    int insertUser(User user);

    /** 动态 set 更新：只更新非 null 的字段 */
    int updateUserSelective(User user);

    /** 删除 */
    int deleteById(Long id);

    /** @Param 多参 + 计数（返回 int，演示 MapperMethod 的行数适配） */
    int countByAge(@Param("min") int min, @Param("max") int max);

    /** ${} 字符串替换排序（注意：这是 SQL 注入高风险写法，仅演示原理） */
    List<User> selectByOrder(@Param("id") Long id, @Param("order") String order);
}
