package org.springframework.build.gcs.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.maven.wagon.TransferFailedException;

class XmlValidator {

  public File getValidatedSource(String key, File source) throws TransferFailedException {
    Path newSource = copyToTempFile(source);
    Exception ex = validate(newSource);
    if (ex != null) {
      throw new TransferFailedException(getFailureMessage(key, newSource), ex);
    }
    return newSource.toFile();
  }

  private Path copyToTempFile(File source) throws TransferFailedException {
    try {
      Path tempFile = Files.createTempFile("xmlvalidator", source.getName());
      Files.copy(source.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
      return tempFile;
    } catch (IOException e) {
      throw new TransferFailedException("Failed to copy source to temporary file prior to validation: " + source.getPath(), e);
    }
  }

  private Exception validate(Path source) throws TransferFailedException {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    // read the whole document (which verifies that it is well-formed)
    try (FileInputStream stream = new FileInputStream(source.toFile())) {
      XMLStreamReader reader = factory.createXMLStreamReader(stream);
      try {
        while (reader.hasNext()) {
          reader.next();
        }
      } catch (XMLStreamException e) {
        return e;
      } finally {
        reader.close();
      }
    } catch (Exception e) {
      throw new TransferFailedException("Failed to validate xml file: " + source, e);
    }

    return null;
  }

  private String getFailureMessage(String key, Path source) {
    if (key.contains("maven-metadata")) {
      return String.format("Received maven-metadata that is not valid xml: %s. "
          + "If this is your first time seeing this issue, please try to rebuild.",
          source);
    } else {
      // this shouldn't happen
      return String.format("Received xml file for upload: %s is not valid xml!", source);
    }
  }
}
