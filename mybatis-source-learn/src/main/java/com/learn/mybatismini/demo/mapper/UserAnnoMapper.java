package com.learn.mybatismini.demo.mapper;

import com.learn.mybatismini.annotations.CacheNamespace;
import com.learn.mybatismini.annotations.Delete;
import com.learn.mybatismini.annotations.Insert;
import com.learn.mybatismini.annotations.Options;
import com.learn.mybatismini.annotations.Param;
import com.learn.mybatismini.annotations.Select;
import com.learn.mybatismini.annotations.Update;
import com.learn.mybatismini.demo.entity.User;

import java.util.List;

/**
 * 注解式 Mapper（不写 XML，SQL 直接写在注解上）。
 *
 * <h3>什么时候用注解、什么时候用 XML？</h3>
 * <ul>
 *   <li><b>简单 CRUD</b>：注解 @Select/@Insert 就够了，少一个文件；</li>
 *   <li><b>动态 SQL（if/foreach/choose）</b>：注解里塞 {@code <script>} 很别扭，必须上 XML。</li>
 * </ul>
 *
 * <p>本接口用 {@code @CacheNamespace} 开启二级缓存（等价于 XML 的 {@code <cache/>}），
 * 并用 {@code @Options} 补充 {@code useGeneratedKeys / keyProperty}。
 */
@CacheNamespace
public interface UserAnnoMapper {

    @Select("SELECT id, user_name, age, email, create_time FROM t_user WHERE id = #{id}")
    User selectById(Long id);

    @Select("SELECT id, user_name, age, email, create_time FROM t_user ORDER BY id")
    List<User> selectAll();

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO t_user(user_name, age, email, create_time) VALUES(#{userName}, #{age}, #{email}, #{createTime})")
    int insertUser(User user);

    @Update("UPDATE t_user SET user_name = #{userName} WHERE id = #{id}")
    int updateUserName(@Param("id") Long id, @Param("userName") String userName);

    @Delete("DELETE FROM t_user WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT COUNT(*) FROM t_user WHERE age BETWEEN #{min} AND #{max}")
    int countByAge(@Param("min") int min, @Param("max") int max);
}
