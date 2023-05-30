package cpw.mods.niofs.layzip;

import cpw.mods.niofs.pathfs.PathFileSystemProvider;
import cpw.mods.niofs.pathfs.PathPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public class LayeredZipFileSystemProvider extends PathFileSystemProvider
{
    public static final String SCHEME    = "jij";
    public static final String INDICATOR = "!";
    public static final String SEPARATOR =  INDICATOR + "/";

    public static final String URI_SPLIT_REGEX = SEPARATOR;


    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) throws IOException
    {
        final String[] sections = uri.getRawSchemeSpecificPart().split(URI_SPLIT_REGEX);
        FileSystem workingSystem = FileSystems.getDefault(); //Grab the normal disk FS.

        String keyPrefix = "";

        if (sections.length > 1) {
            for (int i = 0; i < sections.length - 1; i++)
            {
                String section = sections[i];
                if (section.startsWith("//"))
                    section = section.substring(2);

                section = handleAbsolutePrefixOnWindows(workingSystem, section);
                final Path path = workingSystem.getPath(section).toAbsolutePath();
                workingSystem = getOrCreateNewSystem(keyPrefix, path);
                keyPrefix += path.toString().replace("\\", "/") + INDICATOR;
            }
        }

        String lastSection = sections[sections.length - 1];
        if (lastSection.startsWith("//"))
            lastSection = lastSection.substring(2);


        final Path lastPath = workingSystem.getPath(lastSection).toAbsolutePath();
        return getOrCreateNewSystem(keyPrefix, lastPath);
    }

    private String handleAbsolutePrefixOnWindows(final FileSystem workingSystem, String section)
    {
        if (workingSystem.getClass().getName().toLowerCase(Locale.ROOT).contains("windows")) {
            //This special casing is needed, since else the rooted paths crash on Windows system because:
            // /D:/something is not a valid path on Windows.
            //However, the JDK does not expose the Windows FS types and there are no marker classes, so we use the classname.
            //Because we are fancy like that.
            if (section.startsWith("/"))
                section = section.substring(1); //Turns /D:/something into D:/Something which is a valid windows path.
        }
        return section;
    }

    private FileSystem getOrCreateNewSystem(final Path path)
    {
        return getOrCreateNewSystem("", path);
    }

    private FileSystem getOrCreateNewSystem(String keyPrefix, final Path path)
    {
        final Map<String, ?> args = Map.of("packagePath", path.toAbsolutePath());
        try
        {
            return super.newFileSystem(new URI(super.getScheme() + ":" + keyPrefix + path.toString().replace("\\", "/")), args);
        }
        catch (Exception e)
        {
            throw new UncheckedIOException("Failed to create intermediary FS.", new IOException("Failed to process data.", e));
        }
    }

    @Override
    public Path getPath(final URI uri)
    {
        final String[] sections = uri.getRawSchemeSpecificPart().split("~");
        FileSystem workingSystem = FileSystems.getDefault(); //Grab the normal disk FS.
        if (sections.length > 1) {
            for (int i = 0; i < sections.length - 1; i++)
            {
                final String section = sections[i];
                final Path path = workingSystem.getPath(section);
                workingSystem = getOrCreateNewSystem(path);
            }
        }

        final String lastSection = sections[sections.length - 1];
        return workingSystem.getPath(lastSection);
    }

    @Override
    public FileSystem getFileSystem(final URI uri)
    {
        final String[] sections = uri.getRawSchemeSpecificPart().split("~");
        FileSystem workingSystem = FileSystems.getDefault(); //Grab the normal disk FS.
        if (sections.length > 1) {
            for (int i = 0; i < sections.length - 1; i++)
            {
                final String section = sections[i];
                final Path path = workingSystem.getPath(section);
                workingSystem = getOrCreateNewSystem(path);
            }
        }

        final String lastSection = sections[sections.length - 1];
        final Path lastPath = workingSystem.getPath(lastSection);
        return getOrCreateNewSystem(lastPath);
    }

    @Override
    protected URI buildUriFor(final PathPath path) throws URISyntaxException, IllegalArgumentException
    {
        String prefix = "";

        final URI outerUri = path.getFileSystem().getTarget().toUri();
        prefix = outerUri.getRawSchemeSpecificPart() + SEPARATOR;

        return URI.create("%s:%s%s".formatted(SCHEME, prefix, path).replace("%s/".formatted(SEPARATOR), SEPARATOR));
    }

    @Override
    public Path adaptResolvedPath(final PathPath path)
    {
        if (!path.toString().contains(SEPARATOR))
            return path;

        final Path workingPath = path.getFileSystem().getPath(path.toString().substring(0, path.toString().lastIndexOf(SEPARATOR)) + SEPARATOR);
        final FileSystem workingSystem;
        try
        {
            workingSystem = FileSystems.newFileSystem(workingPath.toUri(), Map.of());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Failed to get sub file system for path!", e);
        }

        return workingSystem.getPath(path.endsWith(SEPARATOR) ? "/" : path.toString().substring(path.toString().lastIndexOf(SEPARATOR) + 2));
    }

    @Override
    public String[] adaptPathParts(final String longstring, final String[] pathParts)
    {
        if(!longstring.endsWith(SEPARATOR))
            return pathParts;

        pathParts[pathParts.length - 1] = pathParts[pathParts.length - 1] + "/";
        return pathParts;
    }
}
