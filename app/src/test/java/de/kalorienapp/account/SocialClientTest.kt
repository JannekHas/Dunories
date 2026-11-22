package de.kalorienapp.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialClientTest {

    @Test
    fun `freunde lesen, privates bleibt null`() {
        val data = SocialData.parse(
            """{"code":"NMPM-XD5D","friends":[{"code":"UPTS-RH2S","name":"Ben","badge":null,"achievements":null,"streak":null},
               {"code":"ABCD-EFGH","name":"Anna","badge":"days_7","achievements":["days_7"],"streak":12}],
               "incoming":[{"code":"WXYZ-2345","name":"Tim"}],"outgoing":[]}""",
        )!!
        assertEquals("NMPM-XD5D", data.code)
        assertNull(data.friends[0].streak)
        assertNull(data.friends[0].achievements)
        assertEquals(12, data.friends[1].streak)
        assertEquals(listOf("days_7"), data.friends[1].achievements)
        assertEquals("Tim", data.incoming.single().name)
    }

    @Test
    fun `fehler beim anfragen`() {
        assertEquals(FriendRequestResult.UNKNOWN_CODE, SocialClient.requestError("""{"error":"unknown_code"}"""))
        assertEquals(FriendRequestResult.SELF, SocialClient.requestError("""{"error":"self"}"""))
        assertEquals(FriendRequestResult.NETWORK, SocialClient.requestError(""))
    }
}
