package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 🚀 MAIN CLASS: DemoApplication (Điểm vào của ứng dụng)
 * 
 * Mục đích:
 *  - Khởi động Spring Boot application
 *  - Quét @Configuration và @Component để đăng ký Bean
 *  - Chuẩn bị server (Tomcat) và kết nối database
 * 
 * Annotation:
 *  - @SpringBootApplication: Kích hoạt:
 *      + @Configuration: Lớp config
 *      + @ComponentScan: Tìm @Component, @Service, @Controller, v.v.
 *      + @EnableAutoConfiguration: Config tự động dựa trên classpath
 * 
 * Luồng khởi động:
 *  1. main() gọi SpringApplication.run()
 *  2. Spring Boot khởi tạo context
 *  3. Quét classpath tìm @Component, @Service, @Configuration
 *  4. DataSourceConfig được load → tạo Primary + Secondary datasource
 *  5. StudentService được load → @PostConstruct gọi initializeSecondaryDb()
 *  6. Tomcat server khởi động trên port 8080
 *  7. Ứng dụng sẵn sàng nhận request
 * 
 * Để chạy:
 *  - IDE: Click vào Main class → Run
 *  - Terminal: ./mvnw spring-boot:run
 *  - JAR: java -jar target/demo-0.0.1-SNAPSHOT.jar
 */
@SpringBootApplication
public class DemoApplication {

	/**
	 * 📍 entry point của ứng dụng
	 * 
	 * @param args: Command line arguments (tùy chọn)
	 * 
	 * Ví dụ:
	 *  java -Dserver.port=9090 -jar app.jar
	 *  → args[] = [server.port=9090]
	 */
	public static void main(String[] args) {
		// Khởi động Spring Boot application
		// Trả về Application Context (quản lý toàn bộ Bean)
		SpringApplication.run(DemoApplication.class, args);
	}

}
