install-java-17:
	mkdir "${JAVA_HOME}"
	curl -fsSL -o /tmp/openjdk.tar "${OPENJDK_FILE}"
	tar -C "${JAVA_HOME}" --strip-components 1 -xf /tmp/openjdk.tar
	update-alternatives --install /usr/bin/java java "${JAVA_HOME}/bin/java" 1000
	update-alternatives --install /usr/bin/javac javac "${JAVA_HOME}/bin/javac" 1000
	update-alternatives --set java "${JAVA_HOME}/bin/java"
	java -version

master:
	sh ./gradlew -Dbuild.snapshot=false -PincludePytorchNativeDependencies -PpytorchFlavor=${PYTORCH_FLAVOR} -PplatformClassifier=${PLATFORM_CLASSIFIER} assemble

PR-%:
	sh ./gradlew -Dbuild.snapshot=true -PincludePytorchNativeDependencies -PpytorchFlavor=${PYTORCH_FLAVOR} -PplatformClassifier=${PLATFORM_CLASSIFIER} assemble

oracle-%:
	# Production builds are made from oracle-x.y.z branches
	sh ./gradlew -Dbuild.snapshot=false -PincludePytorchNativeDependencies -PpytorchFlavor=${PYTORCH_FLAVOR} -PplatformClassifier=${PLATFORM_CLASSIFIER} assemble

user-dev:
	# Local ocibuild
	sh ./gradlew -Dbuild.snapshot=true assemble

output:
	mkdir -p ${ARTIFACT_OUT_DIR}
	cp plugin/build/distributions/*.zip ${ARTIFACT_OUT_DIR}