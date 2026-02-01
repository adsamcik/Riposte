plugins {
    id("com.android.ai-pack")
}

aiPack {
    packName = "soc_optimized"
    dynamicDelivery {
        deliveryType = "install-time"
    }
}
