package actions.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import actions.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@RestController
@Slf4j
public class S3FileController {
	
	private final S3Service s3Service;
	
	
	@GetMapping(value = "/api/files")
	public String file() {
		return "S3파일컨트롤러는 정상";
	}
	@PostMapping(value = "/api/s3/files")
	public void uploadS3File(@RequestPart(value ="file", required=false) MultipartFile file) {
		try {
			s3Service.uploadS3File(file);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}
	
	@GetMapping(value = "/api/s3/files/{fileNo}")
	public ResponseEntity<Resource> downloadS3File(@PathVariable("fileNo") long fileNo) throws Exception {
		return s3Service.downloadS3File(fileNo);
	}
}

