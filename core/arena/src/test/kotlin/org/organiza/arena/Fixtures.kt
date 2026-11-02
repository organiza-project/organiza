package org.organiza.arena

object Fixtures {
    const val CHALLENGE = "c1"

    fun member(name: String) = TestMember(name, ArenaCrypto.newPrivateKey())
}

/** Plays one honest participant, so tests can then corrupt specific things. */
class TestMember(val name: String, val privateKey: ByteArray) {
    val id: String = ArenaCrypto.publicKey(privateKey).joinToString("") { "%02x".format(it) }

    class SignedProof(val message: ArenaMessage.Proof, val salt: ByteArray) {
        val commitment: ByteArray get() = message.commitment
    }

    fun join(): ArenaMessage.Join = Messages.join(Fixtures.CHALLENGE, privateKey, name)

    fun proof(date: String, outcome: DayOutcome, streak: Int, previous: ByteArray?): SignedProof {
        val salt = ArenaCrypto.randomBytes(32)
        return SignedProof(Messages.proof(Fixtures.CHALLENGE, privateKey, date, outcome, streak, salt, previous), salt)
    }

    fun reveal(proofs: List<SignedProof>): ArenaMessage.Reveal =
        Messages.reveal(Fixtures.CHALLENGE, privateKey, proofs.map { it.message.date to it.salt })
}
