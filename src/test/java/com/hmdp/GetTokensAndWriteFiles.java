package com.hmdp;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@SpringBootTest
public class GetTokensAndWriteFiles {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void getTokensAndWriteFiles() throws IOException {
        // 查询前1000个用户
        List<User> users = userService.lambdaQuery().last("limit 1000").list();
        FileWriter fileWriter = null;
        try {
            // 创建文件写入流
            fileWriter = new FileWriter("D:\\tokens.txt");
            for (User user : users) {
                // 随机生成Token
                String token = UUID.randomUUID().toString(true);
                // 将User对象转换为HashMap存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                // 存储到Redis
                String tokenKey = "login:token:" + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
                // 写入Token到文件
                fileWriter.append(token).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }
}