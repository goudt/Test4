package routee.assessment;

import org.junit.Test;

import static org.junit.Assert.*;



public class RestClientTest {

    @Test
    public void authenticateRouteeApiUser() {
        RestClient.getInstance().authenticateRouteeApiUser();
    }
}