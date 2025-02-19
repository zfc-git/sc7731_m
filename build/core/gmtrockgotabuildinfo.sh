#!/bin/bash

UAFS_PATH=bootable/recovery/uafs_rock/uafs.h 

if [ $# -lt 1 ] ; then
echo "Usage: $0 build.prop"
echo "hufeng.mao@generalmobi.com"
exit
fi

echo ""
echo "# begin ROCK GOTA properties"

# true|false,用于手动配置brand与model的开关
GMT_MANUAL_SET=false

# 当GMT_MANUAL_SET宏为true时有用
LOCAL_PROJECT_BRAND_VALUE="Fly"
LOCAL_PROJECT_MODEL_VALUE="IQ 4412"

# 根据项目配置以下内容
LOCAL_PROJECT_BRAND="ro.product.brand"
LOCAL_PROJECT_MODEL="ro.product.model"
LOCAL_PROJECT_VERSION="ro.build.display.id"
LOCAL_PROJECT_SDCARD_SUPPORT="true"
LOCAL_UAFS_VERSION="$(grep "UAFS_VERSION" $UAFS_PATH | awk -F '\"' '{print $2}')"

# DO NOT MODIFY!不要修改这里的值
ROCK_GOTA_BRAND="ro.rock.gota.brand"
ROCK_GOTA_MODEL="ro.rock.gota.model"
ROCK_GOTA_VERSION="ro.rock.gota.version"
ROCK_GOTA_SDCARD_SUPPORT="ro.rock.gota.sd"
ROCK_UAFS_VERSION="ro.rock.gota.uaver"
	
if [ "$GMT_MANUAL_SET" == "true" ] ; then
	echo "$ROCK_GOTA_BRAND=$LOCAL_PROJECT_BRAND_VALUE"
	echo "$ROCK_GOTA_MODEL=$LOCAL_PROJECT_MODEL_VALUE"
else
	echo "$(grep "$LOCAL_PROJECT_BRAND" "$1" | sed "s/$LOCAL_PROJECT_BRAND/$ROCK_GOTA_BRAND/" )"
	echo "$(grep "$LOCAL_PROJECT_MODEL" "$1" | sed "s/$LOCAL_PROJECT_MODEL/$ROCK_GOTA_MODEL/" )"
fi

echo "$(grep "$LOCAL_PROJECT_VERSION" "$1" | sed "s/$LOCAL_PROJECT_VERSION/$ROCK_GOTA_VERSION/" )"_`date +%s`
echo "$ROCK_GOTA_SDCARD_SUPPORT=$LOCAL_PROJECT_SDCARD_SUPPORT"
echo "$ROCK_UAFS_VERSION=$LOCAL_UAFS_VERSION"

echo "# end ROCK GOTA properties"
