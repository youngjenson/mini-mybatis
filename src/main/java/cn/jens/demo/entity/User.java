package cn.jens.demo.entity;

import cn.jens.demo.type.EmailAddress;

/**
 * 用户实体类
 * @author YumJens
 * @date 2026-09-05 15:26
 */
public class User {

    private Integer id;

    private String name;

    private Integer age;

    private EmailAddress email;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public EmailAddress getEmail() {
        return email;
    }

    public void setEmail(EmailAddress email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", email=" + email +
                '}';
    }
}
