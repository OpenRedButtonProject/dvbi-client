LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := org.orbtv.dvbiclient

LOCAL_STATIC_JAVA_LIBRARIES += \
   org.orbtv.companionlibrary \
   androidx.legacy_legacy-support-v4

LOCAL_SRC_FILES := $(call all-subdir-java-files)

include $(BUILD_STATIC_JAVA_LIBRARY)
include $(call all-makefiles-under, $(LOCAL_PATH))

