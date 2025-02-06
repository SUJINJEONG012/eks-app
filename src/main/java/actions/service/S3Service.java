package actions.service;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import actions.entity.AttachmentFile;
import actions.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class S3Service {
	
	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;
	
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;
    
    // Kubernetes에서 마운트된 볼륨 경로를 사용
    private final String DIR_NAME = "/app/data"; 

    
    // 파일 업로드
	@Transactional
	public void uploadS3File(MultipartFile file) throws Exception {
		log.info("S3Service :uploadS3File " );
		
		if(file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		
		// 컨테이너 내부 볼륨 경로 사용
		String filePath = DIR_NAME;
		String attachmentOriginalFileName = file.getOriginalFilename();
		
		UUID uuid = UUID.randomUUID();
		String attachmentFileName = uuid.toString()+ "_" + attachmentOriginalFileName;
		Long attachmentFileSize= file.getSize();
		
		AttachmentFile attachmentFile = AttachmentFile.builder()
				.filePath(filePath)
				.attachmentOriginalFileName(attachmentOriginalFileName)
				.attachmentFileName(attachmentFileName)
				.attachmentFileSize(attachmentFileSize)
				.build();
		
		Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
		
		if(fileNo != null) {
			// 물리적으로 파일을 저장할 경로
			File uploadFile = new File(attachmentFile.getFilePath() + "/" + attachmentFileName);
			file.transferTo(uploadFile);  // 컨테이너 내 /app/data 에 저장됨
			
			// S3 업로드
	        String s3Key = "s3_data/" + uploadFile.getName();
	        amazonS3.putObject(bucketName, s3Key, uploadFile);

	        // S3에 저장되면 컨테이너 내 파일 삭제
	        if (uploadFile.exists()) {
	            uploadFile.delete();
	        }
	        
		}
		
	}
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		
		AttachmentFile attachmentFile;
		Resource resource = null ;
		
		try {
		// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
		attachmentFile =  fileRepository.findById(fileNo)
										.orElseThrow(() -> new NoSuchElementException("파일이 없습니다."));
		
		
		// S3에서 파일 다운로드 (경로 수정)
        String s3Key = "s3_data/" + attachmentFile.getAttachmentFileName();
        S3Object s3Object = amazonS3.getObject(bucketName, s3Key);

        //️⃣ S3 파일 내용을 InputStreamResource로 변환
        S3ObjectInputStream s3is = s3Object.getObjectContent();
        resource = new InputStreamResource(s3is);
		
		}catch(Exception e) {
			return new ResponseEntity<Resource>(resource, null, HttpStatus.NO_CONTENT);
		}
		
		
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition(ContentDisposition
										.builder("attachment")
										.filename(attachmentFile.getAttachmentOriginalFileName())
										.build());
		
//		HttpHeaders headers = new HttpHeaders();
//		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//		headers.setContentDisposition(ContentDisposition
//										.builder("attachment")
//										.filename("file-text.txt")
//										.build());
		
		
		
		
		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
		
	}
	
}