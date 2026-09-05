package cn.jens.mybatis.exception;

/**
 * mini-MyBatis 统一持久化异常。
 *
 * @author YumJens
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
