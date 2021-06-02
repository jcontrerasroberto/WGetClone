import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WGetClone {

    private ExecutorService pool;
    private final ArrayList<String> urls = new ArrayList<>();
    private String mainUrl;

    class Handler extends Thread{
        private String urlToDownload;
        private String dirToSave;
        private String rootDir;
        private String fileName;
        private String pathToSave;
        private Pattern regexLinks = Pattern.compile("(href|src)=\".+?\"",Pattern.CASE_INSENSITIVE);

        public Handler(String mainUrl, String dirToSave){
            this.urlToDownload = mainUrl;
            this.dirToSave = dirToSave;
            this.rootDir = dirToSave;
        }

        public void createDir() throws MalformedURLException {
            URL url = new URL(urlToDownload);
            Path pathOfDir = Paths.get(dirToSave, url.getHost(), url.getPath());
            System.out.println("Saving in "+ pathOfDir.toString());
            if(pathOfDir.getFileName().toString().matches(".+\\..+") && !urlToDownload.endsWith("/")){
                fileName = pathOfDir.getFileName().toString();
                pathOfDir = pathOfDir.getParent();
            }else{
                fileName = "index.html";
            }
            File dir = new File(pathOfDir.toString());
            dir.mkdirs();
            dirToSave = dir.getAbsolutePath();
            pathToSave = Paths.get(dirToSave, fileName).toString();
        }

        public void createFile(HttpURLConnection con) {
            try {
                System.out.println("Downloading " + urlToDownload + " in " + pathToSave + " and file name " + fileName);
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(pathToSave));
                InputStream inputStream = con.getInputStream();
                byte[] buffer = new byte[1024];
                int bufferLength;
                while ((bufferLength = inputStream.read(buffer)) > 0)
                    bos.write(buffer, 0, bufferLength);
                inputStream.close();
                bos.close();
                con.disconnect();
                System.out.println("File " + pathToSave + " downloaded");
            }
            catch (Exception e){
                System.out.println("Error downloading " + urlToDownload);
            }
        }

        public void lookForLinks() throws IOException, URISyntaxException {
            String fileContent = "";
            BufferedReader reader = new BufferedReader(new FileReader(pathToSave));
            String line = reader.readLine();
            while (line != null){
                fileContent += line;
                line = reader.readLine();
            }
            reader.close();
            Matcher finder = regexLinks.matcher(fileContent);
            while (finder.find()){
                String linkTag = finder.group(0);
                String link = linkTag.substring(linkTag.indexOf("\"")+1, linkTag.length()-1);
                if(!link.startsWith("http")){
                    String linkNoParams = link;
                    if (link.contains("?")){
                        linkNoParams = link.substring(0, link.indexOf("?"));
                    }
                    if(!linkNoParams.equals("") && !linkNoParams.equals("/")) {
                        URI relative = new URI(linkNoParams);
                        URI base = new URI(urlToDownload);
                        URI absolute = base.resolve(relative);
                        pool.execute(new Handler(absolute.toString(), rootDir));
                        if (!linkNoParams.matches(".+\\..+")) {
                            String newLink = Paths.get(linkNoParams, "index.html").toString();
                            if (newLink.startsWith("/")) {
                                newLink = Paths.get(rootDir, new URL(urlToDownload).getHost(), newLink).toString();
                            }
                            System.out.println("Replacing  " + link + " to " + newLink);
                            fileContent = fileContent.replace("\"" + link + "\"", "\"" + newLink + "\"");
                        }
                        if (linkNoParams.startsWith("/")) {
                            String newLink = Paths.get(rootDir, new URL(urlToDownload).getHost(), linkNoParams).toString();
                            System.out.println("Replacing  " + Pattern.quote(link) + " to " + newLink);
                            fileContent = fileContent.replace("\"" + link + "\"", "\"" + newLink + "\"");
                        }
                    }
                }else if (!link.startsWith("?")){
                    String linkNoParams = link;
                    if (link.contains("?")){
                        linkNoParams = link.substring(0, link.indexOf("?"));
                    }
                    if(!linkNoParams.equals("") && !linkNoParams.equals("/")) {
                        pool.execute(new Handler(linkNoParams, rootDir));
                        if (!linkNoParams.matches(".+\\..+")) {
                            String newLink = newLink = Paths.get(rootDir, new URL(linkNoParams).getHost(), new URL(linkNoParams).getPath(), "index.html").toString();
                            System.out.println("Replacing  " + linkNoParams + " to " + newLink);
                            fileContent = fileContent.replace("\"" + link + "\"", "\"" + newLink + "\"");
                        } else {
                            String newLink = newLink = Paths.get(rootDir, new URL(linkNoParams).getHost(), new URL(linkNoParams).getFile()).toString();
                            System.out.println("Replacing  " + link + " to " + newLink);
                            fileContent = fileContent.replace("\"" + link + "\"", "\"" + newLink + "\"");
                        }
                    }
                }
            }
            File replaceFile = new File(pathToSave);
            FileWriter fooWriter = new FileWriter(replaceFile, false); // true to append
            // false to overwrite.
            fooWriter.write(fileContent);
            fooWriter.close();

        }

        @Override
        public void run() {
            synchronized (urls){
                if(urls.contains(urlToDownload)){
                    System.out.println("URL " + urlToDownload + " already downloaded");
                    return;
                }
                urls.add(urlToDownload);
            }

            try {

                URL url = new URL(urlToDownload);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(6000);
                int responseCode = con.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK){
                    String contentType = con.getContentType();
                    createDir();
                    createFile(con);
                    if(fileName.matches(".+\\.html")){
                        lookForLinks();
                    }
                }else{
                    System.out.println("Cannot do GET request");
                }

                con.disconnect();

            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }

    }

    public WGetClone() {

        pool = Executors.newFixedThreadPool(25);
        System.out.print("URL to download: ");
        Scanner in = new Scanner(System.in);
        mainUrl = in.nextLine();
        File main = new File("");
        pool.execute(new Handler(mainUrl, main.getAbsolutePath()));
        in.close();

    }

    public static void main(String[] args) {
        WGetClone wGetClone = new WGetClone();
    }
}
