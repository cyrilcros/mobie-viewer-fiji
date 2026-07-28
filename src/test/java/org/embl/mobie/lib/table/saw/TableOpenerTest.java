package org.embl.mobie.lib.table.saw;

import org.embl.mobie.lib.io.StorageLocation;
import org.embl.mobie.lib.table.TableDataFormat;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TableOpenerTest {

    @Test
    public void testOpenExcelFile() throws Exception {

         // Call the method under test
        TableDataFormat tableDataFormat = TableDataFormat.fromPath( "src/test/resources/collections/clem-collection.xlsx" );
        StorageLocation location = new StorageLocation();
        location.absolutePath = "src/test/resources/collections/clem-collection.xlsx";
        Table table = TableOpener.open( location, tableDataFormat );

        //Table table = TableOpener.openExcelFile("src/test/resources/test.xlsx");

        // Verify the table structure
        assertNotNull(table);
        int rowCount = table.rowCount();
        assertEquals(2, rowCount);
        assertEquals("uri", table.columnNames().get(0));
        assertEquals("affine", table.columnNames().get(1));
    }

    @Test
    public void testOpenBomCsvWithNonUtf8DefaultEncoding() throws Exception {
        final File csv = new File( "src/test/resources/collections/segmented-image-collection.csv" );

        final String javaBin = new File( new File( System.getProperty( "java.home" ), "bin" ), "java" ).getAbsolutePath();
        final String classpath = System.getProperty( "java.class.path" );

        final Process process = new ProcessBuilder(
                javaBin,
                "-Dfile.encoding=ISO-8859-1",
                "-cp",
                classpath,
                "org.embl.mobie.lib.table.saw.TableOpenerEncodingProbe",
                csv.getAbsolutePath() )
                .redirectErrorStream( true )
                .start();

        final StringBuilder output = new StringBuilder();
        try ( BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream(), StandardCharsets.UTF_8 ) ) )
        {
            String line;
            while ( ( line = reader.readLine() ) != null )
            {
                output.append( line ).append( '\n' );
            }
        }

        final int exitCode = process.waitFor();
        assertEquals( 0, exitCode, "Probe failed with output:\n" + output );
    }
}
