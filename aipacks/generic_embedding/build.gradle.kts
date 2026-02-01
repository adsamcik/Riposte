plugins {
    id("com.android.ai-pack")
}

aiPack {
    packName = "generic_embedding"
    dynamicDelivery {
        deliveryType = "install-time"
    }
}
