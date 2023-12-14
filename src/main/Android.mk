LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := org.orbtv.dvbiclient

LOCAL_STATIC_JAVA_LIBRARIES += \
   org.orbtv.companionlibrary \
   androidx.legacy_legacy-support-v4

LOCAL_SRC_FILES := $(call all-subdir-java-files)

ASSETS_PATH = "$(PRODUCT_OUT)/org.orbtv.polyfill/resources"
IGNORED := $(shell mkdir -p $(ASSETS_PATH) && cp -r $(LOCAL_PATH)/assets $(ASSETS_PATH))

include $(BUILD_STATIC_JAVA_LIBRARY)
include $(call all-makefiles-under, $(LOCAL_PATH))

