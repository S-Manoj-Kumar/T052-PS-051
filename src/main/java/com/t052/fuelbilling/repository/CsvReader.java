
package com.t052.fuelbilling.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CsvReader {

    public List<CSVRecord> readCsv(Path filePath) throws IOException {

        try (
            var reader = Files.newBufferedReader(filePath);
            var parser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get()
                    .parse(reader)
        ) {
            return parser.getRecords();
        }
    }
}
