fun computeStorageFunFact(totalBytes: Long): String {
    if (totalBytes <= 0) return ""
    
    val FLOPPY_SIZE = 1_474_560L
    val CD_SIZE = 700_000_000L
    val SONG_SIZE = 4_000_000L
    val PHOTO_SIZE = 3_500_000L
    
    val equivalences = mutableListOf<String>()
    
    val floppies = totalBytes / FLOPPY_SIZE
    if (floppies >= 1) equivalences.add(" floppy disks")
    
    val songs = totalBytes / SONG_SIZE
    if (songs >= 1) equivalences.add(" songs")
    
    val photos = totalBytes / PHOTO_SIZE
    if (photos >= 1) equivalences.add(" photos")
    
    if (totalBytes >= CD_SIZE) {
        val cds = totalBytes / CD_SIZE
        equivalences.add(" CDs")
    }
    
    if (equivalences.isEmpty()) return "A few bytes of culture"
    
    val index = (totalBytes % equivalences.size).toInt()
    return "≈  of pure culture"
}

// Test edge cases
println("Test 1 - Small value (100): " + computeStorageFunFact(100))
println("Test 2 - 1 floppy (1474560): " + computeStorageFunFact(1_474_560))
println("Test 3 - 1 song (4000000): " + computeStorageFunFact(4_000_000))
println("Test 4 - Large value (1000000000): " + computeStorageFunFact(1_000_000_000))
