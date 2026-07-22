package in.sp.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartAttendanceSystemApplication {

	public static void main(String[] args) {

		SpringApplication.run(SmartAttendanceSystemApplication.class, args);
        System.out.println("✅ Smart Attendance System Started Successfully!");
        System.out.println("☕ Face Detection: Pure Java Mode (No OpenCV required)");
	}

}
