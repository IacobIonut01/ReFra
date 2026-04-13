plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("ml_models")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
