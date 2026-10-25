.PHONY: run app aab clean test install

run:
	./gradlew assembleDebug

app: run
	cp -f app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk

aab:
	./gradlew bundleRelease
	cp -f app/build/outputs/bundle/release/app-release.aab ./app-release.aab

clean:
	./gradlew clean
	rm -f ./app-debug.apk
	rm -f ./app-release.aab

test:
	./gradlew testDebugUnitTest

install:
	./gradlew installDebug
