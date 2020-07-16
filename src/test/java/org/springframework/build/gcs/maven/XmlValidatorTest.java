package org.springframework.build.gcs.maven;

import static org.junit.Assert.assertNotEquals;

import java.io.File;

import org.apache.maven.wagon.TransferFailedException;
import org.junit.Test;

public class XmlValidatorTest {

  @Test
  public void itValidatesValidXml() throws Exception {
    File source = getFile("valid.xml");
    File output = new XmlValidator().getValidatedSource("some-key", getFile("valid.xml"));
    assertNotEquals(output, source);
  }

  @Test(expected = TransferFailedException.class)
  public void itValidatesInvalidXml() throws Exception {
    new XmlValidator().getValidatedSource("some-key", getFile("invalid.xml"));
  }

  private static File getFile(String resource) {
    try {
      return new File(XmlValidatorTest.class.getResource("/" + resource).toURI());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
