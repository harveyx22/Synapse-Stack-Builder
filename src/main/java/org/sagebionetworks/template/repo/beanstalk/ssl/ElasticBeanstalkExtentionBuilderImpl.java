package org.sagebionetworks.template.repo.beanstalk.ssl;

import static org.sagebionetworks.template.Constants.GLOBAL_RESOURCES_EXPORT_PREFIX;
import static org.sagebionetworks.template.Constants.PROPERTY_KEY_STACK;
import static org.sagebionetworks.template.Constants.PROPERTY_KEY_INSTANCE;
import static org.sagebionetworks.template.Constants.CLOUDWATCH_LOGS_DESCRIPTORS;
import static org.sagebionetworks.template.Constants.LOAD_BALANCER_ALARMS;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.sagebionetworks.template.Constants;
import org.sagebionetworks.template.config.Configuration;
import org.sagebionetworks.template.FileProvider;
import org.sagebionetworks.template.TemplateGuiceModule;
import org.sagebionetworks.template.repo.beanstalk.EnvironmentType;
import org.sagebionetworks.template.repo.beanstalk.LoadBalancerAlarmsConfig;
import org.sagebionetworks.template.repo.cloudwatchlogs.CloudwatchLogsVelocityContextProvider;
import org.sagebionetworks.war.WarAppender;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class ElasticBeanstalkExtentionBuilderImpl implements ElasticBeanstalkExtentionBuilder {


	public static final String SSL_CONF = "ssl.conf";

	public static final String SECURITY_CONF = "security.conf";

	public static final String HTTPD_CONF_D = "httpd/conf.d";

	public static final String TEMPLATES_REPO_EBEXTENSIONS_HTTPS_SSL_CONF = "templates/repo/ebextensions/https-ssl.conf";

	public static final String TEMPLATES_REPO_EBEXTENSIONS_SECURITY_CONF = "templates/repo/ebextensions/security.conf";

	public static final String INSTANCE_CONFIG = "instance.config";

	public static final String DOT_EBEXTENSIONS = ".ebextensions";

	public static final String DOT_PLATFORM = ".platform";

	public static final String HOOKS_POSTDEPLOY = "hooks/postdeploy";

	public static final String TEMPLATE_EBEXTENSIONS_INSTANCE_CONFIG = "templates/repo/ebextensions/instance.config";

	public static final String TEMPLATE_EBEXTENSIONS_BEANSTALK_LOGS_CW_CONFIG = "templates/repo/ebextensions/beanstalk_logs_cloudwatch.config";

	public static final String BEANSTALK_LOGS_CW_CONFIG = "beanstalk_cwlogs.config";
	
	public static final String TEMPLATE_EBEXTENSIONS_BEANSTALK_ALARMS = "templates/repo/ebextensions/beanstalk_alarms.config";

	public static final String BEANSTALK_ALARMS_CONFIG = "beanstalk_alarms.config";

	public static final String REPO_RESTART_SERVICES_SCRIPT = "01_restart_services.sh";
	public static final String TEMPLATES_REPO_RESTART_SERVICES = "templates/repo/01_restart_services.sh.vpt";

	public static final String TEMPLATE_REPO_EBEXTENSIONS_DIR = "templates/repo/ebextensions";
	public static final String TEMPLATE_REPO_PLATFORM_DIR = "templates/repo/platform";
	public static final String TEMPLATE_REPO_PLATPORM_HOOKS_POSTDEPLOY_DIR = TEMPLATE_REPO_PLATFORM_DIR + "/hooks/postdeploy";
	public static final String TEMPLATE_REPO_PLATFORM_HTTPD_CONFD_DIR = TEMPLATE_REPO_PLATFORM_DIR + "/httpd/conf.d";

	CertificateBuilder certificateBuilder;
	VelocityEngine velocityEngine;
	Configuration configuration;
	WarAppender warAppender;
	FileProvider fileProvider;
	CloudwatchLogsVelocityContextProvider cwlContextprovider;
	LoadBalancerAlarmsConfig loadBalancerAlarmsConfig;

	@Inject
	public ElasticBeanstalkExtentionBuilderImpl(CertificateBuilder certificateBuilder, VelocityEngine velocityEngine,
			Configuration configuration, WarAppender warAppender, FileProvider fileProvider, CloudwatchLogsVelocityContextProvider cwlCtxtProvider,
			LoadBalancerAlarmsConfig loadBalancerAlarmsConfig) {
		super();
		this.certificateBuilder = certificateBuilder;
		this.velocityEngine = velocityEngine;
		this.configuration = configuration;
		this.warAppender = warAppender;
		this.fileProvider = fileProvider;
		this.cwlContextprovider = cwlCtxtProvider;
		this.loadBalancerAlarmsConfig = loadBalancerAlarmsConfig;
	}

	@Override
	public File copyWarWithExtensions(File warFile, EnvironmentType envType) {
		VelocityContext context = new VelocityContext();
		context.put("s3bucket", configuration.getConfigurationBucket());
		// Get the certificate information
		context.put("certificates", certificateBuilder.buildNewX509CertificatePair());
		// EnvironmentType in context
		context.put("envType", envType);
		// CloudwatchLog descriptors
		context.put(CLOUDWATCH_LOGS_DESCRIPTORS, cwlContextprovider.getLogDescriptors(envType));
		
		String stack = configuration.getProperty(PROPERTY_KEY_STACK);
		String instance = configuration.getProperty(PROPERTY_KEY_INSTANCE);
		
		context.put("stack", stack);
		context.put("instance", instance);
		
		// Exported resources prefix
		context.put(GLOBAL_RESOURCES_EXPORT_PREFIX, Constants.createGlobalResourcesExportPrefix(stack));
		// Inject the alarms configuration for the environment
		context.put(LOAD_BALANCER_ALARMS, loadBalancerAlarmsConfig.getOrDefault(envType, Collections.emptyList()));

		// add the files to the copy of the war
		return warAppender.appendFilesCopyOfWar(warFile, new Consumer<File>() {

			@Override
			public void accept(File directory) {
				// ensure the .ebextensions directory exists
				File ebextensionsDirectory = fileProvider.createNewFile(directory, DOT_EBEXTENSIONS);
				ebextensionsDirectory.mkdirs();
				// ensure the .platform directory exists
				File platformDirectory = fileProvider.createNewFile(directory, DOT_PLATFORM);
				platformDirectory.mkdirs();
				// ensure the .platform/httpd/conf.d directory exists.
				File confDDirectory = fileProvider.createNewFile(platformDirectory, HTTPD_CONF_D);
				confDDirectory.mkdirs();
				// Ensure the .platform/hooks/postdeploy directory exists
				File hooksPostDeployDirectory = fileProvider.createNewFile(platformDirectory, HOOKS_POSTDEPLOY);
				hooksPostDeployDirectory.mkdirs();

				processFiles(context, fileProvider.createNewFile(directory, TEMPLATE_REPO_EBEXTENSIONS_DIR), ebextensionsDirectory);
				processFiles(context, fileProvider.createNewFile(directory, TEMPLATE_REPO_PLATFORM_HTTPD_CONFD_DIR), confDDirectory);
				processFiles(context, fileProvider.createNewFile(directory, TEMPLATE_REPO_PLATPORM_HOOKS_POSTDEPLOY_DIR), hooksPostDeployDirectory);

//				// https-instance.config in .ebextensions
//				Template httpInstanceTempalte = velocityEngine.getTemplate(TEMPLATE_EBEXTENSIONS_INSTANCE_CONFIG);
//				File resultFile = fileProvider.createNewFile(ebextensionsDirectory, INSTANCE_CONFIG);
//				addTemplateAsFileToDirectory(httpInstanceTempalte, context, resultFile);
//				// Beanstalk logs CloudwatchLogs config in .ebextensions
//				resultFile = fileProvider.createNewFile(ebextensionsDirectory, BEANSTALK_LOGS_CW_CONFIG);
//				Template beanstalkClodwatchConf = velocityEngine.getTemplate(TEMPLATE_EBEXTENSIONS_BEANSTALK_LOGS_CW_CONFIG);
//				addTemplateAsFileToDirectory(beanstalkClodwatchConf, context, resultFile);
//				// Beanstalk environment alarms in .ebextensions
//				resultFile = fileProvider.createNewFile(ebextensionsDirectory, BEANSTALK_ALARMS_CONFIG);
//				Template beanstalkAlarms = velocityEngine.getTemplate(TEMPLATE_EBEXTENSIONS_BEANSTALK_ALARMS);
//				addTemplateAsFileToDirectory(beanstalkAlarms, context, resultFile);
//
//				// SSL conf in ,platform/httpd/conf.d
//				resultFile = fileProvider.createNewFile(confDDirectory, SSL_CONF);
//				Template sslconf = velocityEngine.getTemplate(TEMPLATES_REPO_EBEXTENSIONS_HTTPS_SSL_CONF);
//				addTemplateAsFileToDirectory(sslconf, context, resultFile);
//				// ModSecurity conf in .platform/httpd/conf.d
//				resultFile = fileProvider.createNewFile(confDDirectory, SECURITY_CONF);
//				Template modSecurityConf = velocityEngine.getTemplate(TEMPLATES_REPO_EBEXTENSIONS_SECURITY_CONF);
//				addTemplateAsFileToDirectory(modSecurityConf, context, resultFile);
//
//				// Restart services script in .platform/hooks/postdeploy
//				resultFile = fileProvider.createNewFile(hooksPostDeployDirectory, REPO_RESTART_SERVICES_SCRIPT);
//				Template restartServicesScript = velocityEngine.getTemplate(TEMPLATES_REPO_RESTART_SERVICES);
//				addTemplateAsFileToDirectory(restartServicesScript, context, resultFile);
			}
		});

	}

	/**
	 * Merge the passed template and context and save the results as a new file in
	 * the passed directory with the given name.
	 * 
	 * @param template
	 * @param context
	 * @param resultFile
	 */
	public void addTemplateAsFileToDirectory(Template template, VelocityContext context, File resultFile) {
		try (Writer writer = fileProvider
				.createFileWriter(resultFile)) {
			template.merge(context, writer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void processFiles(VelocityContext context, File sourceDir, File destDir) {
		List<File> sourceFiles = fileProvider.listFilesInDirectory(sourceDir);
		for (File f:sourceFiles) {
			Template httpInstanceTemplate = velocityEngine.getTemplate(f.getPath());
			File resultFile = fileProvider.createNewFile(destDir, f.getName());
			addTemplateAsFileToDirectory(httpInstanceTemplate, context, resultFile);
		}
	}
	
	/**
	 * Helper to run the actual builder
	 * @param args
	 */
	public static void main(String[] args) {
		Injector injector = Guice.createInjector(new TemplateGuiceModule());
		ElasticBeanstalkExtentionBuilder builder = injector.getInstance(ElasticBeanstalkExtentionBuilder.class);
		File resultWar = builder.copyWarWithExtensions(new File(args[0]), EnvironmentType.REPOSITORY_SERVICES);
		System.out.println(resultWar.getAbsolutePath());
	}

}
