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

    
//    // 파일 업로드
//	@Transactional
//	public void uploadS3File(MultipartFile file) throws Exception {
//		log.info("S3Service :uploadS3File " );
//		
//		if(file == null) {
//			throw new Exception("파일 전달 오류 발생");
//		}
//		
//		// 컨테이너 내부 볼륨 경로 사용
//		String filePath = DIR_NAME;
//		String attachmentOriginalFileName = file.getOriginalFilename();
//		
//		UUID uuid = UUID.randomUUID();
//		String attachmentFileName = uuid.toString()+ "_" + attachmentOriginalFileName;
//		Long attachmentFileSize= file.getSize();
//		
//		AttachmentFile attachmentFile = AttachmentFile.builder()
//				.filePath(filePath)
//				.attachmentOriginalFileName(attachmentOriginalFileName)
//				.attachmentFileName(attachmentFileName)
//				.attachmentFileSize(attachmentFileSize)
//				.build();
//		
//		Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
//		
//		if(fileNo != null) {
//			// 물리적으로 파일을 저장할 경로
//			File uploadFile = new File(attachmentFile.getFilePath() + "/" + attachmentFileName);
//			file.transferTo(uploadFile);  // 컨테이너 내 /app/data 에 저장됨
//			
//			// S3 업로드
//	        String s3Key = "s3_data/" + uploadFile.getName();
//	        amazonS3.putObject(bucketName, s3Key, uploadFile);
//
////	        // S3에 저장되면 컨테이너 내 파일 삭제
////	        if (uploadFile.exists()) {
////	            uploadFile.delete();
////	        }
//	        
//		}
//		
//	}
    
    @Transactional
    public void uploadS3File(MultipartFile file) throws Exception {
        log.info("S3Service :uploadS3File ");
        
        if (file == null) {
            throw new Exception("파일 전달 오류 발생");
        }
        
        // 로컬 파일 경로 설정
        // DIR_NAME은 /app/data로 정의되어 있으므로, 이를 이용해 로컬 경로 설정
        String filePath = DIR_NAME;  // "/app/data"로 설정된 디렉토리 경로 사용
        
        String attachmentOriginalFileName = file.getOriginalFilename();
        UUID uuid = UUID.randomUUID();
        String attachmentFileName = uuid.toString() + "_" + attachmentOriginalFileName;
        Long attachmentFileSize = file.getSize();
        
        // AttachmentFile 객체 생성
        AttachmentFile attachmentFile = AttachmentFile.builder()
                .filePath(filePath)
                .attachmentOriginalFileName(attachmentOriginalFileName)
                .attachmentFileName(attachmentFileName)
                .attachmentFileSize(attachmentFileSize)
                .build();
        
        // 파일 정보를 DB에 저장
        Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
        
        if (fileNo != null) {
            // 로컬에 파일 저장할 경로 생성
            File dir = new File(filePath);
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    log.info("디렉토리가 생성되었습니다: " + filePath);
                } else {
                    log.error("디렉토리 생성에 실패했습니다: " + filePath);
                    throw new Exception("디렉토리 생성 실패");
                }
            }
            
            // 파일을 로컬에 저장
            File uploadFile = new File(attachmentFile.getFilePath() + "/" + attachmentFileName);
            file.transferTo(uploadFile);  // 파일 로컬에 저장
            
            log.info("파일이 로컬 임시 디렉토리에 저장되었습니다: " + uploadFile.getAbsolutePath());
            
            // S3에 파일 업로드
            String s3Key = "s3_data/" + uploadFile.getName();  // S3에 저장될 경로
            amazonS3.putObject(bucketName, s3Key, uploadFile);  // S3에 파일 업로드
            
            log.info("파일이 S3에 업로드되었습니다: " + s3Key);
            
            // S3에 파일이 업로드되면 로컬 파일 삭제 (주석처리한 부분을 사용 시 삭제 가능)
//            if (uploadFile.exists()) {
//                uploadFile.delete();
//            }
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

        //️ S3 파일 내용을 InputStreamResource로 변환
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
	
	// 파일 삭제
	@Transactional
	public ResponseEntity<String> deleteFile(long fileNo) {
		try {
			//DB에서 파일 가져오기
			AttachmentFile attachmentFile = fileRepository.findById(fileNo)
					.orElseThrow(() -> new NoSuchElementException("파일이 존재하지 않습니다."));
			// S3에서 파일 삭제
			String s3Key = "s3_data/" + attachmentFile.getAttachmentFileName();
			amazonS3.deleteObject(bucketName, s3Key);  // S3에서 삭제
			
		     // DB에서 파일 정보 삭제
			
		     fileRepository.deleteById(fileNo);
		     return new ResponseEntity<>("파일이 성공적으로 삭제되었습니다.", HttpStatus.OK);
		}catch(Exception e) {
			log.error("파일 삭제 중 오류 발생 :", e.getMessage() );
			 return new ResponseEntity<>("파일 삭제에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}