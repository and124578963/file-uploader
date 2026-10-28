package org.my_ration.file_uploader.service;

/** Готовые к записи байты и расширение, соответствующее их настоящему формату. */
public record ProcessedImage(byte[] data, String extension) {
}
