package org.bsc.confluence.model;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import org.apache.commons.io.IOUtils;
import org.bsc.confluence.ConfluenceService.Storage;
import org.bsc.functional.Tuple2;
import org.bsc.markdown.ToConfluenceSerializer;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.Node;
import org.pegdown.ast.RootNode;

public class SiteProcessor {
   
    /**
     * 
     * @param uri
     * @param onSuccess
     * @return
     */
    public static <T> T processUri( 
            final java.net.URI uri, 
            java.util.function.BiFunction<Optional<Exception>,Optional<java.io.InputStream>, T> callback) 
    {
        Objects.requireNonNull(uri, "uri is null!");
        Objects.requireNonNull(callback, "callback is null!");

        final String scheme = uri.getScheme();

        Objects.requireNonNull(scheme, String.format("uri [%s] is invalid!", String.valueOf(uri)));
        
        final String source = uri.getRawSchemeSpecificPart();

        java.io.InputStream result = null;

        if ("classpath".equalsIgnoreCase(scheme)) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            result = cl.getResourceAsStream(source);

            if (result == null) {

                cl = Site.class.getClassLoader();

                result = cl.getResourceAsStream(source);

                final Exception ex = new Exception(String.format("resource [%s] doesn't exist in classloader", source));
                return callback.apply( Optional.of(ex), Optional.empty());

            }

        } else {

            try {
                
                java.net.URL url = uri.toURL();

                result = url.openStream();

            } catch (IOException e) {
                final Exception ex = new Exception(String.format("error opening url [%s]!", source), e);
                return callback.apply( Optional.of(ex), Optional.empty());
            }
        }

        return callback.apply( Optional.empty(), Optional.of(result));
    }

    
    /**
    *
    * @param uri
    * @return
    * @throws Exception
    */
   public static <T> T processPageUri(
           final java.net.URI uri, 
           final String homePageTitle,
           final BiFunction<Optional<Exception>, 
           Tuple2<Optional<java.io.InputStream>, Storage.Representation>, T> callback)
   {
       Objects.requireNonNull(uri, "uri is null!");

       String scheme = uri.getScheme();

       Objects.requireNonNull(scheme, String.format("uri [%s] is invalid!", String.valueOf(uri)));

       final String source = uri.getRawSchemeSpecificPart();

       final String path = uri.getRawPath();

       final boolean isMarkdown = (path != null && path.endsWith(".md"));
       final boolean isStorage = (path != null && (path.endsWith(".xml") || path.endsWith(".xhtml")));

       final Storage.Representation representation = (isStorage) ? Storage.Representation.STORAGE
               : Storage.Representation.WIKI;

       java.io.InputStream result = null;

       if ("classpath".equalsIgnoreCase(scheme)) {
           ClassLoader cl = Thread.currentThread().getContextClassLoader();

           result = cl.getResourceAsStream(source);

           if (result == null) {
               // getLog().warn(String.format("resource [%s] doesn't exist in context
               // classloader", source));

               cl = Site.class.getClassLoader();

               final java.io.InputStream is = cl.getResourceAsStream(source);

               try {
                   result = (isMarkdown) ? processMarkdown(is, homePageTitle) : is;
                   if (result == null) {
                       final Exception ex = new Exception(String.format("page [%s] doesn't exist in classloader", source));
                       return callback.apply( Optional.of(ex), Tuple2.of(Optional.empty(), representation) );
                   }
               } catch (IOException e) {
                   final Exception ex = new Exception(String.format("error processing markdown for page [%s] ", source));
                   return callback.apply( Optional.of(ex), Tuple2.of(Optional.empty(), representation) );
               }


           }

       } else {

           try {

               java.net.URL url = uri.toURL();

               final java.io.InputStream is = url.openStream();

               result = (isMarkdown) ? processMarkdown(is, homePageTitle) : is;

           } catch (IOException e) {
               final Exception ex = new Exception(String.format("error opening/processing page [%s]!", source), e);
               return callback.apply( Optional.of(ex), Tuple2.of(Optional.empty(), representation) );
           }
       }

       return callback.apply( Optional.empty(), Tuple2.of(Optional.of(result), representation));
   }

    
    /**
    *
    * @param uri
    * @return
    * @throws Exception
    */
   public static <T> T processUriContent(   
               final java.net.URI uri,                                  
               final String homePageTitle,
               final BiFunction<java.io.InputStream, Storage.Representation, T> onSuccess 
           ) throws /* ProcessUri */Exception 
   {
       Objects.requireNonNull(uri, "uri is null!");

       String scheme = uri.getScheme();

       Objects.requireNonNull(scheme, String.format("uri [%s] is invalid!", String.valueOf(uri)));

       final String source = uri.getRawSchemeSpecificPart();

       final String path = uri.getRawPath();

       final boolean isMarkdown = (path != null && path.endsWith(".md"));
       final boolean isStorage = (path != null && (path.endsWith(".xml") || path.endsWith(".xhtml")));

       final Storage.Representation representation = (isStorage) ? Storage.Representation.STORAGE
               : Storage.Representation.WIKI;

       java.io.InputStream result = null;

       if ("classpath".equalsIgnoreCase(scheme)) {
           ClassLoader cl = Thread.currentThread().getContextClassLoader();

           result = cl.getResourceAsStream(source);

           if (result == null) {
               // getLog().warn(String.format("resource [%s] doesn't exist in context
               // classloader", source));

               cl = Site.class.getClassLoader();

               final java.io.InputStream is = cl.getResourceAsStream(source);

               result = (isMarkdown) ? processMarkdown(is, homePageTitle) : is;

               if (result == null) {
                   throw new Exception(String.format("resource [%s] doesn't exist in classloader", source));
               }

           }

       } else {

           try {

               java.net.URL url = uri.toURL();

               final java.io.InputStream is = url.openStream();

               result = (isMarkdown) ? processMarkdown(is, homePageTitle) : is;

           } catch (IOException e) {
               throw new Exception(String.format("error opening url [%s]!", source), e);
           }
       }

       return onSuccess.apply(result, representation);
   }

    
    /**
     * 
     * @param is
     * @return
     */
    static java.io.InputStream processMarkdown(final java.io.InputStream is, final String homePageTitle)
            throws IOException {

        final char[] contents = IOUtils.toCharArray(is);

        final PegDownProcessor p = new PegDownProcessor(ToConfluenceSerializer.extensions());

        final RootNode root = p.parseMarkdown(contents);

        ToConfluenceSerializer ser = new ToConfluenceSerializer() {

            @Override
            protected void notImplementedYet(Node node) {

                final int lc[] = ToConfluenceSerializer.lineAndColFromNode(new String(contents), node);
                throw new UnsupportedOperationException(String.format("Node [%s] not supported yet. line=[%d] col=[%d]",
                        node.getClass().getSimpleName(), lc[0], lc[1]));
            }

            @Override
            protected String getHomePageTitle() {
                return homePageTitle;
            }

        };

        root.accept(ser);

        return new java.io.ByteArrayInputStream(ser.toString().getBytes());
    }


}
