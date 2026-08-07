plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("sunny_model_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
