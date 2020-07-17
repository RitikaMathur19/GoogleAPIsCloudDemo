/**
 * 
 */
package com.ritu.googleAPIs.controller;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.ritu.googleAPIs.dto.FileItemDTO;

/**
 * 
 *
 */
@Controller
public class HomePageController {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	// private static final List<String> SCOPES =
	// Collections.singletonList(DriveScopes.DRIVE);

	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE,
			"https://www.googleapis.com/auth/drive.install");

	private static final String USER_IDENTIFIER_KEY = "MY_DUMMY_USER";

	@Value("${google.oauth.callback.uri}")
	private String CALLBACK_URI;

	@Value("${google.secret.key.path}")
	private Resource gdSecretKeys;

	@Value("${google.credentials.folder.path}")
	private Resource credentialsFolder;

	/*
	 * @Value("${google.service.account.key}") private Resource serviceAccountKey;
	 */
	private GoogleAuthorizationCodeFlow flow;

	@PostConstruct
	public void init() throws Exception {
		GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(gdSecretKeys.getInputStream()));
		flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
				.setDataStoreFactory(new FileDataStoreFactory(credentialsFolder.getFile())).build();
		logger.info("Inside init-->secrets details" + secrets.getDetails() + " , flow-->" + flow.getClientId()
				+ "getClientAuthentication: " + flow.getClientAuthentication() + " ");
	}

	@GetMapping(value = { "/" })
	public String showHomePage() throws Exception {
		boolean isUserAuthenticated = false;

		Credential credential = flow.loadCredential(USER_IDENTIFIER_KEY);

		if (credential != null) {
			boolean tokenValid = credential.refreshToken();
			if (tokenValid) {
				isUserAuthenticated = true;
			}
		}
		logger.info("Inside showHomePage-->credentials are:" + credential);
		return isUserAuthenticated ? "dashboard.html" : "index.html";
	}

	@GetMapping(value = { "/googlesignin" })
	public void doGoogleSignIn(HttpServletResponse response) throws Exception {
		GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
		String redirectURL = url.setRedirectUri(CALLBACK_URI).setAccessType("offline").build();
		logger.info("redirectURL-->" + redirectURL);
		logger.info("Inside google sign in -->credentials are:" + flow.getCredentialDataStore());
		response.sendRedirect(redirectURL);
	}

	@GetMapping(value = { "/oauth2callback" })
	public String saveAuthorizationCode(HttpServletRequest request) throws Exception {
		String code = request.getParameter("code");
		logger.info("Inside oauth -->code is:" + code);
		if (code != null) {
			saveToken(code);

			return "dashboard.html";
		}

		return "index.html";
	}

	private void saveToken(String code) throws Exception {
		GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(CALLBACK_URI).execute();
		flow.createAndStoreCredential(response, USER_IDENTIFIER_KEY);
		logger.info("Inside save token -->credentials are:" + flow.getCredentialDataStore());

	}

	@GetMapping(value = { "/create" })
	public void createFile(HttpServletResponse response) throws Exception {
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("SpringBootGoogleDriveDemo").build();
		logger.info("Inside create file ,credentials are-->" + cred.toString());
		File file = new File();
		file.setName("music.jpg");

		FileContent content = new FileContent("image/jpeg", new java.io.File("C:\\Ritika\\sampleFiles\\music.jpg"));
		File uploadedFile = drive.files().create(file, content).setFields("id").execute();

		String fileReference = String.format("{fileID: '%s'}", uploadedFile.getId());
		response.getWriter().write(fileReference);
	}

	

	@GetMapping(value = { "/listfiles" }, produces = { "application/json" })
	public @ResponseBody List<FileItemDTO> listFiles() throws Exception {
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
		logger.info("Inside list files-->");
		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("SpringBootGoogleDriveDemo").build();

		List<FileItemDTO> responseList = new ArrayList<>();

		FileList fileList = drive.files().list().setFields("files(id,name,thumbnailLink)").execute();
		for (File file : fileList.getFiles()) {
			FileItemDTO item = new FileItemDTO();
			item.setId(file.getId());
			item.setName(file.getName());
			item.setThumbnailLink(file.getThumbnailLink());
			responseList.add(item);
		}

		return responseList;
	}

	@PostMapping(value = { "/makepublic/{fileId}" }, produces = { "application/json" })
	public @ResponseBody Message makePublic(@PathVariable(name = "fileId") String fileId) throws Exception {
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("SpringBootGoogleDriveDemo").build();
		logger.info("Inside makePublic-->");
		Permission permission = new Permission();
		permission.setType("anyone");
		permission.setRole("reader");

		drive.permissions().create(fileId, permission).execute();

		Message message = new Message();
		message.setMessage("Permission has been successfully granted.");
		logger.info("Permission has been successfully granted.");
		return message;
	}

	@DeleteMapping(value = { "/deletefile/{fileId}" }, produces = "application/json")
	public @ResponseBody Message deleteFile(@PathVariable(name = "fileId") String fileId) throws Exception {
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
		logger.info("Inside Delete file-->");
		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("SpringBootGoogleDriveDemo").build();

		drive.files().delete(fileId).execute();
		logger.info("Delete file-->" + fileId);
		Message message = new Message();
		message.setMessage("File has been deleted.");
		return message;
	}

	@GetMapping(value = { "/createfolder/{folderName}" }, produces = "application/json")
	public @ResponseBody Message createFolder(@PathVariable(name = "folderName") String folder) throws Exception {
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("SpringBootGoogleDriveDemo").build();
		logger.info("Inside creating folder-->"+folder);
		File file = new File();
		file.setName(folder);
		file.setMimeType("application/vnd.google-apps.folder");

		drive.files().create(file).execute();

		Message message = new Message();
		message.setMessage("Folder has been created successfully.");
		return message;
	}
	
	@GetMapping(value = { "/uploadinfolder" })
	public void uploadFileInFolder(HttpServletResponse response) throws Exception {
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);

		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("SpringBootGoogleDriveDemo").build();
		logger.info("Inside upload file in folder-->");
		File file = new File();
		file.setName("dance.jpg");
		file.setParents(Arrays.asList("1LsgH0ZgPshli_NXSI400P1Q6WQRL2lXn"));

		FileContent content = new FileContent("image/jpeg", new java.io.File("C:\\Ritika\\sampleFiles\\dance.jpg"));
		File uploadedFile = drive.files().create(file, content).setFields("id").execute();

		String fileReference = String.format("{fileID: '%s'}", uploadedFile.getId());
		logger.info("file id-->"+fileReference);
		response.getWriter().write(fileReference);
	}

	/*
	 * @GetMapping(value = { "/servicelistfiles" }, produces = { "application/json"
	 * }) public @ResponseBody List<FileItemDTO> listFilesInServiceAccount() throws
	 * Exception { Credential cred =
	 * GoogleCredential.fromStream(serviceAccountKey.getInputStream());
	 * 
	 * GoogleClientRequestInitializer keyInitializer = new
	 * CommonGoogleClientRequestInitializer();
	 * 
	 * Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY,
	 * null).setHttpRequestInitializer(cred)
	 * .setGoogleClientRequestInitializer(keyInitializer).build();
	 * 
	 * List<FileItemDTO> responseList = new ArrayList<>();
	 * 
	 * FileList fileList =
	 * drive.files().list().setFields("files(id,name,thumbnailLink)").execute(); for
	 * (File file : fileList.getFiles()) { FileItemDTO item = new FileItemDTO();
	 * item.setId(file.getId()); item.setName(file.getName());
	 * item.setThumbnailLink(file.getThumbnailLink()); responseList.add(item); }
	 * 
	 * return responseList; }
	 */

	class Message {
		private String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

}