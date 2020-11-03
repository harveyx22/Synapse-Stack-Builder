package org.sagebionetworks.template.s3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.template.Constants.PROPERTY_KEY_STACK;

import java.util.Arrays;

import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sagebionetworks.template.TemplateGuiceModule;
import org.sagebionetworks.template.config.RepoConfiguration;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.google.inject.Guice;
import com.google.inject.Injector;

@ExtendWith(MockitoExtension.class)
public class S3BucketBuilderIntegrationTest {
	
	@Mock
	private RepoConfiguration mockConfig;

	@Mock
	private S3Config mockS3Config;

	@Mock
	private AmazonS3 mockS3Client;

	@Mock
	private AWSSecurityTokenService mockStsClient;
	
	@Mock
	private GetCallerIdentityResult mockGetCallerIdentityResult;
	
	@Captor
	private ArgumentCaptor<String> stringCaptor;

	private S3BucketBuilderImpl builder;
	private String stack;
	private String accountId;
	
	@BeforeEach
	public void before() {
		Injector injector = Guice.createInjector(new TemplateGuiceModule());
		VelocityEngine velocityEngine = injector.getInstance(VelocityEngine.class);
		
		builder = new S3BucketBuilderImpl(mockS3Client, mockStsClient, mockConfig, mockS3Config, velocityEngine);
		
		stack = "dev";
		accountId = "12345";
		
		when(mockConfig.getProperty(PROPERTY_KEY_STACK)).thenReturn(stack);
		when(mockStsClient.getCallerIdentity(any())).thenReturn(mockGetCallerIdentityResult);
		when(mockGetCallerIdentityResult.getAccount()).thenReturn(accountId);
	}

	@Test
	public void testInventoryBucketPolicy() {

		S3BucketDescriptor inventoryBucket = new S3BucketDescriptor();
		inventoryBucket.setName("dev.inventory");
		
		S3BucketDescriptor bucket = new S3BucketDescriptor();
		bucket.setName("dev.bucket");
		bucket.setInventoryEnabled(true);
		
		when(mockS3Config.getInventoryBucket()).thenReturn(inventoryBucket.getName());
		when(mockS3Config.getBuckets()).thenReturn(Arrays.asList(inventoryBucket, bucket));
		
		String expectedPolicy = "{\n"
				+ "  \"Version\":\"2012-10-17\",\n"
				+ "  \"Statement\":[\n"
				+ "    {\n"
				+ "      \"Sid\":\"InventoryPolicy\",\n"
				+ "      \"Effect\":\"Allow\",\n"
				+ "      \"Principal\": {\"Service\": \"s3.amazonaws.com\"},\n"
				+ "      \"Action\":\"s3:PutObject\",\n"
				+ "      \"Resource\":[\"arn:aws:s3:::dev.inventory/*\"],\n"
				+ "      \"Condition\": {\n"
				+ "          \"ArnLike\": {\n"
				+ "              \"aws:SourceArn\": [\"arn:aws:s3:::dev.bucket\"]\n"
				+ "         },\n"
				+ "         \"StringEquals\": {\n"
				+ "             \"aws:SourceAccount\": \"12345\",\n"
				+ "             \"s3:x-amz-acl\": \"bucket-owner-full-control\"\n"
				+ "          }\n"
				+ "       }\n"
				+ "    }\n"
				+ "  ]\n"
				+ "}";
		
		// Call under test
		builder.buildAllBuckets();
		
		verify(mockS3Client).setBucketPolicy("dev.inventory", expectedPolicy);
		
	}
	
	@Test
	public void testInventoryBucketPolicyWithMultipleBuckets() {

		S3BucketDescriptor inventoryBucket = new S3BucketDescriptor();
		inventoryBucket.setName("dev.inventory");
		
		S3BucketDescriptor bucketOne = new S3BucketDescriptor();
		bucketOne.setName("dev.bucketOne");
		bucketOne.setInventoryEnabled(true);
		

		S3BucketDescriptor bucketTwo = new S3BucketDescriptor();
		bucketTwo.setName("dev.bucketTwo");
		bucketTwo.setInventoryEnabled(true);
		
		
		when(mockS3Config.getInventoryBucket()).thenReturn(inventoryBucket.getName());
		when(mockS3Config.getBuckets()).thenReturn(Arrays.asList(inventoryBucket, bucketOne, bucketTwo));
		
		String expectedPolicy = "{\n"
				+ "  \"Version\":\"2012-10-17\",\n"
				+ "  \"Statement\":[\n"
				+ "    {\n"
				+ "      \"Sid\":\"InventoryPolicy\",\n"
				+ "      \"Effect\":\"Allow\",\n"
				+ "      \"Principal\": {\"Service\": \"s3.amazonaws.com\"},\n"
				+ "      \"Action\":\"s3:PutObject\",\n"
				+ "      \"Resource\":[\"arn:aws:s3:::dev.inventory/*\"],\n"
				+ "      \"Condition\": {\n"
				+ "          \"ArnLike\": {\n"
				+ "              \"aws:SourceArn\": [\"arn:aws:s3:::dev.bucketOne\",\"arn:aws:s3:::dev.bucketTwo\"]\n"
				+ "         },\n"
				+ "         \"StringEquals\": {\n"
				+ "             \"aws:SourceAccount\": \"12345\",\n"
				+ "             \"s3:x-amz-acl\": \"bucket-owner-full-control\"\n"
				+ "          }\n"
				+ "       }\n"
				+ "    }\n"
				+ "  ]\n"
				+ "}";
		
		// Call under test
		builder.buildAllBuckets();
		
		verify(mockS3Client).setBucketPolicy("dev.inventory", expectedPolicy);
		
	}
	
}
