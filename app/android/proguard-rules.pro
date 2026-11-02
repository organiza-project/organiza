# Release shrinking rules for Organiza (ADR-0014).

# SQLCipher's native code calls back into these classes by name.
-keep class net.zetetic.database.** { *; }

# BouncyCastle is used through its lightweight API only (ADR-0011). Its JCA provider classes
# reference platform APIs Android lacks, which are never reached.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
