#!/usr/bin/env groovy
pipeline {
    agent any
    parameters {
        string(name: 'BranchPath', defaultValue: 'j731', description: '')
        choice(name: 'BuildType', choices: 'release\ntest', description: 'test=build and run tests, release=build and create package.zip')
        string(name: 'ResourcesFolder', defaultValue: 'C:\\Publish\\_Resources', description: '')
        string(name: 'MiscFolder', defaultValue: 'C:\\Publish\\Misc', description: '')
        
        //    string(name: 'OverrideVersion', defaultValue: '', description: '')
    }
	environment { 
		//scriptDir = "${WORKSPACE}\\_jenkins"
        //msbuild0 = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Current\\Bin\\msbuild.exe"
		//defaultVersion = "2020.1.1.0"
		gulpDir = "D:\\gulpBuild" 
    }
	stages {
	    stage ("Checkout"){
	        steps {
                echo ("Checking out source code")
                //checkout([$class: 'TeamFoundationServerScm', credentialsConfigurer: [$class: 'AutomaticCredentialsConfigurer'], projectPath: '$/SassConverter', serverUrl: 'http://videvsql001:8080/tfs/viDesktop/', useOverwrite: true, useUpdate: true, workspaceName: 'Hudson-${JOB_NAME}'])
	            git(
                   url: 'https://viglobal2@dev.azure.com/viglobal2/Product/_git/viGlobal',
                   credentialsId: 'a32b6345-33c8-46df-bfde-7af65312bf8a',
                   branch: "${params.BranchPath}"
                )
	            
	        }
	    }
// 		stage('Build All') {
// 			when {
// 				expression {params.BuildType == 'test'}
// 			}
// 			steps {			
// 		        echo "Building entire solution for testing"
// 				bat "\"${env.msbuild}\" ${WORKSPACE}\\Xcelerate.sln /t:Clean;Build;ResolveReferences /p:Configuration=Debug"
//             }
// 		}
		
		stage('Build Debug') {
			when {
				expression {params.BuildType == 'test'}
			}
			steps {
				echo "Building entire solution in Debug Mode for testing"
				bat "\"${env.msbuild}\" \"${WORKSPACE}\\viDesktop.sln\" /t:Clean;Build;ResolveReferences /p:Configuration=Debug "
				
				echo "copy license files"
				bat "xcopy \"${WORKSPACE}\\lib\\*.lic\" \"${WORKSPACE}\\bin\" /Y"
				
				echo "remove stuff that should not be published"
				bat "if exist \"${WORKSPACE}\\FederationMetadata\" rmdir \"${WORKSPACE}\\FederationMetadata\" /s /q"
			}
		}
 		stage('Run Unit Tests') {
 			environment { 
 				nunit = "C:\\Program Files (x86)\\NUnit.org\\nunit-console\\nunit3-console.exe" 
 			}
			when {
				expression {params.BuildType == 'test'}
			}
			steps {
			    parallel nunit: {
     				echo "running tests"
     				//format as nunit2 so xUnit plugin can display results
     				bat "xcopy \"${WORKSPACE}\\lib\\*.lic\" \"${WORKSPACE}\\viDesktopService.Test\\bin\\Debug\" /Y"
     				bat "\"${env.nunit}\" \"${WORKSPACE}\\viDesktopService.Test\\bin\\Debug\\viDesktopService.Test.dll\"  -result:TestResult.xml;format=nunit2"
			    }
		    }
 		}
		stage('Build Release') {
			environment { 
				publishDir = "C:\\Publish\\${params.Version}" 
				package1 = "${env.publishDir}\\Pkg1 - Apply latest build to v2020"
				package2 = "${env.publishDir}\\Pkg2 - Upgrade to v2020 from v2018"
				//resourcesFolder = "C:\\Publish\\_Resources"
				//miscFolder = "C:\\Publish\\Misc"
				remainMaster = "ReMain.Master"
			}
			when {
				expression {params.BuildType == 'release'}
			}
			steps {
				echo "Building entire solution in Release Mode for publish"
				bat "\"${env.msbuild}\" \"${WORKSPACE}\\viDesktop.sln\" /t:Build;ResolveReferences /p:Configuration=Release /p:DeployOnBuild=true,PublishProfile=\"${WORKSPACE}\\lib\\publish.pubxml\",publishUrl=\"${env.package1}\""
				
				echo "copy license files"
				bat "xcopy \"${WORKSPACE}\\lib\\*.lic\" \"${env.package1}\\bin\" /Y"
				
				echo "remove files in Temp folder, generate empty changedb.aspx file"
				bat "del \"${env.package1}\\Temp\\*.*\" /s /q "
				bat "type NUL >  \"${env.package1}\\Temp\\changedb.aspx\""

				echo "remove folder FederationMettadata"
				bat "if exist \"${env.package1}\\FederationMetadata\" rmdir \"${env.package1}\\FederationMetadata\" /s /q"

				echo "remove from css folder viDesktop-custom.css and custom.css"
				bat "if exist \"${WORKSPACE}\\css\\viDesktop-custom.css\" del \"${WORKSPACE}\\css\\viDesktop-custom.css\" "
				bat "if exist \"${WORKSPACE}\\css\\custom.css\" del \"${WORKSPACE}\\css\\custom.css\" "
				
				echo "copy resources folder"
				bat "xcopy \"${params.ResourcesFolder}\" \"${env.package1}\\_Resources\" /e/i/y"

				echo "copy misc folder"
				bat "xcopy \"${params.MiscFolder}\" \"${env.publishDir}\\Misc\" /e/i/y"

				echo "duplicate package1"
				bat "xcopy \"${env.package1}\" \"${env.package2}\\\" /e/y "
				
				echo "remove ReMain.Master"
				bat "del \"${env.package1}\\Common\\${env.remainMaster}\" /s /q "
				bat "del \"${env.package1}\\viRecruitInternal\\${env.remainMaster}\" /s /q "
				bat "del \"${env.package1}\\viRecruitSearchFirm\\${env.remainMaster}\" /s /q "
				bat "del \"${env.package1}\\viRecruitSelfApply\\${env.remainMaster}\" /s /q "
				bat "del \"${env.package1}\\viRecruitSelfUpdate\\${env.remainMaster}\" /s /q "
				bat "del \"${env.package1}\\viRecruitSurvey\\${env.remainMaster}\" /s /q "
			}
		}
		stage('Package and deploy') {
			environment { 
				publishDir = "D:\\PublishBuilds\\${params.Version}" 
				packageDir = "D:\\Ready Build Packages\\${params.Version}-Build" 
				versionLabel = getVersionLabel("${params.Version}", "${currentBuild.number}")
			}
			when {
				expression {params.BuildType == 'releasea'}
			}
			steps {
				
				echo "Clean up build folder, delete previous build:"
				bat "if exist \"${env.packageDir}\\\" rmdir \"${env.packageDir}\" /s /q"
				bat "mkdir \"${env.packageDir}\""

				echo "Copy built files, remove un-needed files:"
				bat "xcopy \"${env.publishDir}\" \"${env.packageDir}\" /e"
				bat "if exist \"${env.packageDir}\\sqlclr\\*.dacpac\" del \"${env.packageDir}\\sqlclr\\*.dacpac\""
				bat "if exist \"${env.packageDir}\\sqlclr\\*.sql\" del \"${env.packageDir}\\sqlclr\\*.sql\""
				bat "if exist \"${env.packageDir}\\service\\XcelerateUploadDir\\\" rmdir \"${env.packageDir}\\service\\XcelerateUploadDir\" /s /q"

				echo "Copy xcelerate add-in MSI, builder MSI, and How-to documents:"
				bat "if exist \"${env.packageDir}\\Addin\\\" rmdir \"${env.packageDir}\\Addin\" /s /q"
				bat "if exist \"${env.packageDir}\\Builder\\\" rmdir \"${env.packageDir}\\Builder\" /s /q"
				bat "mkdir \"${env.packageDir}\\Addins\""
				bat "xcopy \"D:\\PublishingTools\\XcelerateInstaller\\XcelerateInstaller\\XcelerateMachineInstaller64Bit\\bin\\Release\\*Addin*${env.versionLabel}*msi\" \"${env.packageDir}\\Addins\""
				bat "xcopy \"D:\\PublishingTools\\XcelerateInstaller\\XcelerateInstaller\\XcelerateMachineInstaller64Bit\\bin\\Release\\*Builder*${env.versionLabel}*msi\" \"${env.packageDir}\\Addins\""
				bat "xcopy \"D:\\xcelerate\\How to Install Add-ins Silently.txt\" \"${env.packageDir}\\Addins\""
				bat "xcopy \"D:\\xcelerate\\How to Install Add-ins.pdf\" \"${env.packageDir}\\Addins\""
				bat "if exist \"${env.packageDir}\\Addins\\*.wixpdb\" del \"${env.packageDir}\\Addins\\*.wixpdb\""

				echo "Copy Tools - ADExplorer, URL Rewrite MSI, xcelerate Encryption Tool:"
				bat "if exist \"${env.packageDir}\\Tools\\\" rmdir \"${env.packageDir}\\Tools\" /s /q"
				bat "mkdir \"${env.packageDir}\\Tools\""
				bat "xcopy \"D:\\xcelerate\\tools\\*\" \"${env.packageDir}\\Tools\""

				echo "Remove all web.config files:"
				bat "if exist \"${env.packageDir}\\Adminportal\\Web*config\" del \"${env.packageDir}\\Adminportal\\Web*config\""
				bat "if exist \"${env.packageDir}\\service\\Web*config\" del \"${env.packageDir}\\service\\Web*config\""
				bat "if exist \"${env.packageDir}\\webapi\\Web*config\" del \"${env.packageDir}\\webapi\\Web*config\""
				bat "if exist \"${env.packageDir}\\filesync\\FileSynchronizer.exe.config\" del \"${env.packageDir}\\filesync\\FileSynchronizer.exe.config\""

				echo "Copy ConfigTemplates, WorksheetsReports, and Packages:"
				bat "xcopy \"%WORKSPACE%\\XcelerateInstaller\\ConfigTemplates\" \"${env.packageDir}\\ConfigTemplates\\*\" /s"
				bat "xcopy \"%WORKSPACE%\\XcelerateInstaller\\WorksheetsReports\" \"${env.packageDir}\\WorksheetsReports\\*\" /s"
				bat "xcopy \"%WORKSPACE%\\XcelerateInstaller\\Scripts\\Processes\\Packages\\*.zip\" \"${env.packageDir}\\Processes\\Packages\\*\" /s"

				echo "copy licence text file"
				bat "copy \"D:\\xcelerate\\ThirdPartyLicenses.txt\" \"${env.packageDir}\\Adminportal\\ThirdPartyLicenses.txt\" "
				bat "copy \"D:\\xcelerate\\ThirdPartyLicenses.txt\" \"${env.packageDir}\\webapi\\ThirdPartyLicenses.txt\" "
				bat "copy \"D:\\xcelerate\\ThirdPartyLicenses.txt\" \"${env.packageDir}\\service\\ThirdPartyLicenses.txt\" "

				echo "Copy missing DLLs:"
				bat "copy \"D:\\PublishingTools\\MakeAutomatedPackage_files\\dsofile.dll\" \"${env.packageDir}\\filesync\\dsofile.dll\" "
				bat "xcopy \"D:\\PublishingTools\\MakeAutomatedPackage_files\\network_dll\" \"${env.packageDir}\\Adminportal\\bin\" /s /y"
				bat "xcopy \"D:\\PublishingTools\\MakeAutomatedPackage_files\\network_dll\" \"${env.packageDir}\\service\\bin\" /s /y"
				bat "xcopy \"D:\\PublishingTools\\MakeAutomatedPackage_files\\network_dll\" \"${env.packageDir}\\webapi\\bin\" /s /y"
				
				echo "Digital Sign the installer:"
				bat "\"C:\\Program Files (x86)\\Windows Kits\\8.0\\bin\\x86\\signtool.exe\" sign /f \"D:\\code signing\\sign_SHA1.pfx\" /p x!r8PWD /tr http://tsa.starfieldtech.com /td SHA256 /d \"xcelerate Installer\" \"${env.packageDir}\\installer\\Xcelerate.Installer.exe\""

				echo "Create Shortcut to the installer:"
				dir ("${WORKSPACE}\\_jenkins") {
					bat "cscript.exe /nologo createShortcut.vbs \"${env.packageDir}\" \"D:\\Ready Build Packages\\Setup-${env.packageName}\" "
				}
				echo "Zip:"
				bat "powershell.exe -nologo -noprofile -command \"& { Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::CreateFromDirectory('${env.packageDir}', 'D:\\Ready Build Packages\\Setup-${env.PackageName}.zip') }\""

				echo "Copy to Network Share:"
				bat "REM copy \"D:\\Ready Build Packages\\Setup-${env.PackageName}.zip\" \"\\\\192.168.2.222\\d\$\\Setup-${env.PackageName}.zip\""
				bat "copy \"D:\\Ready Build Packages\\Setup-${env.PackageName}.zip\" \"\\\\fs01\\shares\\departments\\development\\builds\\Setup-${env.PackageName}.zip\""
				bat "REM copy \"D:\\Ready Build Packages\\Setup-${env.PackageName}.zip\" \"\\\\QA-01\\xcelerate_WIP\\Setup-${env.PackageName}.zip\""
			}
		}
	}
}
