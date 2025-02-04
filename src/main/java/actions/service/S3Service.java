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
    
    private final String DIR_NAME = "s3_data";
    
    // 파일 업로드
	@Transactional
	public void uploadS3File(MultipartFile file) throws Exception {
		log.info("S3Service :uploadS3File " );
		// DB저장
		// C:/CE/97.data/s3_data에 파일 저장 -> S3 전송 및 저장 (putObject)
		// /Users/jeongsujin/01backend/cloudserver/97_data/s3_data
		
		if(file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		
		// 파일이 존재한다면 
		// 파일경로
		String filePath = "/Users/jeongsujin/01backend/cloudserver/97_data/" + DIR_NAME;
		//String filePath = "/home/ubunt/" + DIR_NAME;
		
		String attachmentOriginalFileName = file.getOriginalFilename();
		UUID uuid = UUID.randomUUID();
		String attachmentFileName = uuid.toString() + "_" + attachmentOriginalFileName;
		Long attachmentFileSize = file.getSize();
		
		// 빌더를 이용해서 객체 만들거임 => 이렇게 하면 새로운 엔티티 객체가 만들어짐
		AttachmentFile attachmentFile = AttachmentFile.builder()
										.filePath(filePath)
										.attachmentOriginalFileName(attachmentOriginalFileName)
										.attachmentFileName(attachmentFileName)
										.attachmentFileSize(attachmentFileSize)
										.build();
		
		// save로 디비에 저장
		Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
		
		if(fileNo !=null) {
			// 물리로 저장한다. 
			File uploadFile = new File(attachmentFile.getFilePath() + "/" + attachmentFileName);
			// 로컬에 저장
			file.transferTo(uploadFile);
		
			// s3 전송 및 저장 
			// bucketName,
			// key: 버킷내부에 저장되는 객체가 저장되는 경로 + 파일명(객체명)
			String s3Key = DIR_NAME + "/" +  uploadFile.getName();
			amazonS3.putObject(bucketName, s3Key, uploadFile);
			
			// s3에 저장이 되면, 로컬에 있는 파일은 삭제
			if(uploadFile.exists()) {
				uploadFile.delete();
				
			
			}
		}
	}
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		
		AttachmentFile attachmentFile = null;
		Resource resource = null;
		
		try {
		// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
		attachmentFile =  fileRepository.findById(fileNo)
										.orElseThrow(() -> new NoSuchElementException("파일이 없습니다."));
		
		
		S3Object s3Object= amazonS3.getObject(bucketName, DIR_NAME + "/" + attachmentFile.getAttachmentFileName());
		
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