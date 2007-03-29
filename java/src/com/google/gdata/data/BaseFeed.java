/* Copyright (c) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.google.gdata.data;

import com.google.gdata.util.common.xml.XmlWriter;
import com.google.gdata.util.common.xml.XmlWriter.Namespace;
import com.google.gdata.client.Query;
import com.google.gdata.client.Service;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.Namespaces;
import com.google.gdata.util.NotModifiedException;
import com.google.gdata.util.ParseException;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.XmlParser;
import com.google.gdata.util.XmlParser.ElementHandler;

import org.xml.sax.Attributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * The BaseFeed class is an abstract base class that represents a
 * generic GData feed object, based primarily on the data model for
 * an {@code <atom:feed>} element.  It is extended to represent
 * OpenSearch RSS channel elements, and also supports generalized
 * extensibility using a defined ExtensionProfile and/or by stored
 * extended data as an XmlBlob.
 * <p>
 * The BaseFeedClass is a generic class that is parameterized by the
 * type of Entry that will be contained within the feed.  The base
 * class contains all the necessary parsing and generation code for
 * feed extension data, but can be subclassed to create subtypes that
 * contain convenience APIs for accessing extension elements and
 * entries.
 * <p>
 * An instance can be initialized by parsing an Atom 1.0 feed
 * from a Reader or by directly initializing its component
 * elements.  It can generate an XML representation of the feed
 * to an XmlWriter in either Atom 1.0 or RSS 2.0 format.
 * <p>
 * Here is the Relax-NG schema that represents an Atom 1.0
 * feed:
 * <pre>
 * AtomFeed =
 *  element atom:feed {
 *    atomCommonAttributes,
 *    (atomAuthor*
 *     atomCategory*
 *     atomContributor*
 *     atomGenerator?
 *     atomIcon?
 *     atomId
 *     atomLink*
 *     atomLogo?
 *     atomRights?
 *     atomSubtitle?
 *     atomTitle
 *     atomUpdated
 *     extensionElement*),
 *     atomEntry*
 *   }
 * </pre>
 *
 * Because the Feed schema differs from the Source schema only by the
 * presence of the entries, the Feed class derives its base property
 * model and parsing/generation implementations from the Source class.
 * <p>
 * The BaseFeed class implements the {@link Kind.Adaptable} interface, meaning
 * it is possible to create new {@link Kind.Adaptor} subtypes that defines
 * a custom extension model (and associated convience APIs) for a BaseFeed
 * subtypes that use Atom/RSS extensions to extend the content model for a
 * particular type of data.
 * <p>
 * An {@link Kind.Adaptor} subclass of BaseFeed should do the following:
 * <ul>
 * <li>Include a {@link Kind.Term} annotation on the class declaration that
 * defines the {@link Category} term value for the GData kind handled by the
 * adaptor.</li>
 * <li>Provide a constructor that takes a {@code Class} and {@code BaseFeed}
 * parameter as an argument that is used when adapting a generic feed type to
 * a more specific one.</li>
 * <li>Implement the {@link Kind.Adaptor#declareExtensions(ExtensionProfile)}
 * method and use it to declare the extension model for the adapted instance
 * within the profile passed as a parameter.   This is used to auto-extend
 * an extension profile when kind Category tags are found during parsing of
 * content.</li>
 * <li>Expose convenience APIs to retrieve and set extension attributes, with
 * an implementions that delegates to {@link ExtensionPoint} methods to
 * store/retrieve the extension data.
 * </ul>
 *
 * 
 * 
 */
abstract public class BaseFeed<F extends BaseFeed, E extends BaseEntry>
    extends Source
    implements Kind.Adaptable, Kind.Adaptor {

  /**
   * The FeedState class provides a simple structure that encapsulates
   * the attributes of an Atom feed that should be shared with a shallow
   * copy if the feed is adapted to a more specific BaseFeed
   * {@link Kind.Adaptor} subtypes.
   * <p>
   * <b>Note: Feed entries are not part of feed shared state, because
   * the entry lists will need to be typed differently for adapted
   * instances.</b>  This means that entries that are created, updated, or
   * deleted in an adapted feed will not be reflected in the base feed
   * used to construct it.  The reverse is also true: changes made to a base
   * feed will not be reflected in any adapted instances of the feed.
   *
   * @see BaseFeed#BaseFeed(Class, BaseFeed)
   */
  protected static class FeedState {

    /** Service associated with the feed. */
    public Service service;

    /** Specifies whether the feed can be posted to. */
    public boolean canPost = true;

    /** OpenSearch: number of search results (feed entries). */
    public int totalResults = Query.UNDEFINED;

    /** OpenSearch: start index. */
    public int startIndex = Query.UNDEFINED;

    /** OpenSearch: items per page. */
    public int itemsPerPage = Query.UNDEFINED;

    /** Adaptable helper */
    public Kind.Adaptable adaptable = new Kind.AdaptableHelper();
  }

  /**
   * Basic state for this feed.   May be shared across multiple adapted
   * instances associated with the same logical feed.
   */
  protected FeedState feedState;

  /**
   * Class used to construct new entry instance, initialized at construction.
   */
  protected Class<? extends E> entryClass;


  /** Feed entries. */
  protected LinkedList<E> entries = new LinkedList<E>();

  /**
   * Copy constructor that initializes a new BaseFeed instance to have
   * identical contents to another instance, using a shared reference to
   * the same {@link FeedState}.  {@link Kind.Adaptor} subclasses
   * of {@code BaseFeed} can use this constructor to create adaptor
   * instances of an entry that share state with the original.
   *
   * @param entryClass
   *          Class used to construct new Entry instances for the Feed.
   */
  protected BaseFeed(Class<? extends E> entryClass) {
    feedState = new FeedState();
    this.entryClass = entryClass;
  }

  /**
   * Copy constructor that initializes a new BaseFeed instance to have
   * identical contents to another instance, using a shared reference to
   * the same {@link FeedState}.  {@link Kind.Adaptor} subclasses
   * of {@code BaseFeed} can use this constructor to create adaptor
   * instances of a feed that share state with the original.
   */
  protected BaseFeed(Class<? extends E> entryClass, BaseFeed sourceFeed) {

    super(sourceFeed);
    feedState = sourceFeed.feedState;
    this.entryClass = entryClass;

  }


  /**
   * {@inheritDoc}
   * <p>
   * The implementation of this method for BaseFeed will declare any
   * extensions associated with the contained entry type.
   */
  public void declareExtensions(ExtensionProfile extProfile) {

    // Create an instance of the associated entry class and declare its
    // extensions.
    E entry = createEntry();
    extProfile.addDeclarations(entry);
  }

  /**
   * Returns that GData {@link Service} instance tassociated with this feed.
   */
  public Service getService() { return feedState.service; }

  /**
   * Sets that GData {@link Service} instance associated with this feed.
   */
  public void setService(Service v) { feedState.service = v; }

  /**
   * Gets the property that indicates if it is possible to post new entries
   * to the feed.
   */
  public boolean getCanPost() { return feedState.canPost; }

  /**
   * Sets the property that indicates if it is possible to post new entries
   * to the feed.
   */
  public void setCanPost(boolean v) { feedState.canPost = v; }

  /**
   * Gets the total number of results associated with this feed.  The value
   * may be larger than the number of contained entries for paged feeds.
   * A value of {@link Query#UNDEFINED} indicates the total size is undefined.
   */
  public int getTotalResults() { return feedState.totalResults; }

  /**
   * Sets the total number of results associated with this feed.  The value
   * may be larger than the number of contained entries for paged feeds.
   * A value of {@link Query#UNDEFINED} indicates the total size is undefined.
   */
  public void setTotalResults(int v) { feedState.totalResults = v; }

  /**
   * Gets the starting index of the contained entries for paged feeds.
   * A value of {@link Query#UNDEFINED} indicates the start index is undefined.
   */
  public int getStartIndex() { return feedState.startIndex; }

  /**
   * Sets the starting index of the contained entries for paged feeds.
   * A value of {@link Query#UNDEFINED} indicates the start index is undefined.
   */
  public void setStartIndex(int v) { feedState.startIndex = v; }

  /**
   * Gets the number of items that will be returned per page for paged feeds.
   * A value of {@link Query#UNDEFINED} indicates the page item count is
   * undefined.
   */
  public int getItemsPerPage() { return feedState.itemsPerPage; }

  /**
   * Sets the number of items that will be returned per page for paged feeds.
   * A value of {@link Query#UNDEFINED} indicates the page item count is
   * undefined.
   */
  public void setItemsPerPage(int v) { feedState.itemsPerPage = v; }

  public List<E> getEntries() { return entries; }

  // Implementation of Adaptable methods
  //
  public void addAdaptor(Kind.Adaptor adaptor) {
    feedState.adaptable.addAdaptor(adaptor);
  }

  public Collection<Kind.Adaptor> getAdaptors() {
    return feedState.adaptable.getAdaptors();
  }

  public <A extends Kind.Adaptor> A getAdaptor(Class<A> adaptorClass) {
    return feedState.adaptable.getAdaptor(adaptorClass);
  }

  /**
   * Creates a new entry for the feed.
   */
  public E createEntry() {

    E entry;
    try {
      entry = entryClass.newInstance();
    } catch (InstantiationException e) {
      throw new IllegalStateException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }

    // Propagate the associated service (if any)
    if (feedState.service != null) {
      entry.setService(feedState.service);
    }

    return entry;
  }


  /** Returns the entry post link for the feed. */
  public Link getEntryPostLink() {
    Link postLink = getLink(Link.Rel.ENTRY_POST, Link.Type.ATOM);
    return postLink;
  }


  /** Returns the self link for the feed. */
  public Link getSelfLink() {
    Link postLink = getLink(Link.Rel.SELF, Link.Type.ATOM);
    return postLink;
  }


  /**
   * Returns the current representation of the feed by requesting it from
   * the associated service using the feed's self link.
   *
   * @return the current state of the feed.
   */
  public F getSelf() throws IOException, ServiceException {
    if (feedState.service == null) {
      throw new ServiceException("Feed is not associated with a GData service");
    }
    Link selfLink = getSelfLink();
    if (selfLink == null) {
      throw new UnsupportedOperationException("Feed cannot be retrieved");
    }
    URL feedUrl = new URL(selfLink.getHref());
    try {
      return (F)feedState.service.getFeed(feedUrl, this.getClass(),
          srcState.updated);
    } catch (NotModifiedException e) {
      return (F)this;
    }
  }


  /**
   * Inserts a new Entry into the feed, if the feed is currently
   * associated with a Service.
   *
   * @return the inserted Entry returned by the Service.
   *
   * @throws ServiceException
   *           If there is no associated GData service or the service is
   *           unable to perform the insertion.
   *
   * @throws UnsupportedOperationException
   *           If insert is not supported for the target feed.
   *
   * @throws IOException
   *           If there is an error communicating with the GData service.
   */
  public <T extends E> T insert(T newEntry)
      throws ServiceException, IOException {
    if (feedState.service == null) {
      throw new ServiceException(
          "Entry is not associated with a GData service");
    }
    Link postLink = getEntryPostLink();
    if (postLink == null) {
      throw new UnsupportedOperationException("Media cannot be inserted");
    }
    URL postUrl = new URL(postLink.getHref());
    return feedState.service.insert(postUrl, newEntry);
  }

  /**
   * Inserts a new media resource into the feed, if the feed is currently
   * associated with a Service.
   *
   * @return the inserted media Entry returned by the Service.
   *
   * @throws ServiceException
   *           If there is no associated GData service or the service is
   *           unable to perform the insertion.
   *
   * @throws UnsupportedOperationException
   *           If insert is not supported for the target feed.
   *
   * @throws IOException
   *           If there is an error communicating with the GData service.
   */
  public E insert(MediaSource media) throws ServiceException, IOException {
    return insert(media, entryClass);
  }

  /**
   * Inserts a new media resource into the feed, if the feed is currently
   * associated with a Service.  This method is meant for subclasses to
   * use when they support heterogeneous feeds.
   *
   * @return the inserted media Entry returned by the Service.
   *
   * @throws ServiceException
   *           If there is no associated GData service or the service is
   *           unable to perform the insertion.
   *
   * @throws UnsupportedOperationException
   *           If insert is not supported for the target feed.
   *
   * @throws IOException
   *           If there is an error communicating with the GData service.
   */
  protected <T extends E> T insert(MediaSource media, Class<T> mediaEntryClass)
      throws ServiceException, IOException {
    if (feedState.service == null) {
      throw new ServiceException(
          "Entry is not associated with a GData service");
    }
    Link postLink = getEntryPostLink();
    if (postLink == null) {
      throw new UnsupportedOperationException("Media cannot be inserted");
    }
    URL postUrl = new URL(postLink.getHref());
    return feedState.service.insert(postUrl, mediaEntryClass, media);
  }

  /**
   * Generates XML in the Atom format.
   *
   * @param   w
   *            Output writer.
   *
   * @param   extProfile
   *            Extension profile.
   *
   * @throws  IOException
   */
  public void generateAtom(XmlWriter w,
                           ExtensionProfile extProfile) throws IOException {

    generateFeedStart(extProfile, w, null);

    generateEntries(w, extProfile);

    generateFeedEnd(w);
  }

  private void generateEntries(XmlWriter w, ExtensionProfile extProfile) throws
      IOException {
    // Generate all entries
    w.startRepeatingElement();
    for (E entry: entries) {
      entry.generateAtom(w, extProfile);
    }
    w.endRepeatingElement();
  }

  /**
   * Closes everything that was opened by
   * {@link #generateFeedStart}.
   *
   * @param w
   * @throws IOException
   */
  public void generateFeedEnd(XmlWriter w) throws IOException {
    w.endElement(Namespaces.atomNs, "feed");
  }

  /**
   * Generates everything that's in the feed up and not including to the entries.
   *
   * The idea is to use generateStart(), write the entries end then
   * call {@link #generateFeedEnd(com.google.gdata.util.common.xml.XmlWriter)}
   * to avoid having to add entries to a list and keep them in memory.
   *
   * @param extProfile
   * @param w
   * @param namespaces extra namespace declarations
   * @throws IOException
   */
  public void generateFeedStart(ExtensionProfile extProfile,
                                XmlWriter w,
                                Collection<Namespace> namespaces) throws
      IOException {
    Vector<Namespace> nsDecls =
      new Vector<Namespace>(namespaceDeclsAtom);
    nsDecls.addAll(extProfile.getNamespaceDecls());

    generateStartElement(w, Namespaces.atomNs, "feed", null, nsDecls);

    // Generate base feed elements
    generateInnerAtom(w, extProfile);

    // Generate OpenSearch elements
    if (feedState.totalResults != Query.UNDEFINED) {
      w.simpleElement(Namespaces.openSearchNs, "totalResults", null,
                      String.valueOf(feedState.totalResults));
    }

    if (feedState.startIndex != Query.UNDEFINED) {
      w.simpleElement(Namespaces.openSearchNs, "startIndex", null,
                      String.valueOf(feedState.startIndex));
    }

    if (feedState.itemsPerPage != Query.UNDEFINED) {
      w.simpleElement(Namespaces.openSearchNs, "itemsPerPage", null,
                      String.valueOf(feedState.itemsPerPage));
    }

    // Invoke ExtensionPoint.
    generateExtensions(w, extProfile);
  }


  /**
   * Generates XML in the RSS format.
   *
   * @param   w
   *            Output writer.
   *
   * @param   extProfile
   *            Extension profile.
   *
   * @throws  IOException
   */
  public void generateRss(XmlWriter w,
                          ExtensionProfile extProfile) throws IOException {

    Vector<XmlWriter.Namespace> nsDecls =
      new Vector<XmlWriter.Namespace>(namespaceDeclsRss);
    nsDecls.addAll(extProfile.getNamespaceDecls());

    w.startElement(Namespaces.rssNs, "rss", rssHeaderAttrs, nsDecls);

    generateStartElement(w, Namespaces.rssNs, "channel", null, null);

    if (srcState.id != null) {
      w.simpleElement(Namespaces.atomNs, "id", null, srcState.id);
    }

    if (xmlBlob != null) {
      String lang = xmlBlob.getLang();
      if (lang != null) {
        w.simpleElement(Namespaces.rssNs, "language", null, lang);
      }
    }

    if (srcState.updated != null) {
      w.simpleElement(Namespaces.rssNs, "lastBuildDate", null,
                      srcState.updated.toStringRfc822());
    }

    w.startRepeatingElement();
    for (Category cat: srcState.categories) {
      cat.generateRss(w);
    }
    w.endRepeatingElement();

    if (srcState.title != null) {
      srcState.title.generateRss(w, "title",
          TextConstruct.RssFormat.PLAIN_TEXT);
    }

    if (srcState.subtitle != null) {
      srcState.subtitle.generateRss(w, "description",
          TextConstruct.RssFormat.FULL_HTML);
    } else {
      w.simpleElement(Namespaces.rssNs, "description", null, null);
    }

    Link htmlLink = getHtmlLink();
    if (htmlLink != null) {
      w.simpleElement(Namespaces.rssNs, "link", null, htmlLink.getHref());
    }

    if (srcState.logo != null || srcState.icon != null) {
      w.startElement(Namespaces.rssNs, "image", null, null);
      w.simpleElement(Namespaces.rssNs, "url", null,
                      srcState.logo != null ? srcState.logo : srcState.icon);
      if (srcState.title != null) {
        srcState.title.generateRss(w, "title",
            TextConstruct.RssFormat.PLAIN_TEXT);
      }
      if (htmlLink != null) {
        w.simpleElement(Namespaces.rssNs, "link", null, htmlLink.getHref());
      }
      w.endElement(Namespaces.rssNs, "image");
    }

    if (srcState.rights != null) {
      srcState.rights.generateRss(w, "copyright",
           TextConstruct.RssFormat.PLAIN_TEXT);
    }

    if (srcState.authors.size() > 0) {
      srcState.authors.get(0).generateRss(w, "managingEditor");
    }

    if (srcState.generator != null) {
      String name = srcState.generator.getName();
      if (name != null) {
        w.simpleElement(Namespaces.rssNs, "generator", null, name);
      }
    }

    if (feedState.totalResults != Query.UNDEFINED) {
      w.simpleElement(Namespaces.openSearchNs, "totalResults", null,
                      String.valueOf(feedState.totalResults));
    }

    if (feedState.startIndex != Query.UNDEFINED) {
      w.simpleElement(Namespaces.openSearchNs, "startIndex", null,
                      String.valueOf(feedState.startIndex));
    }

    if (feedState.itemsPerPage != Query.UNDEFINED) {
      w.simpleElement(Namespaces.openSearchNs, "itemsPerPage", null,
                      String.valueOf(feedState.itemsPerPage));
    }

    // Invoke ExtensionPoint.
    generateExtensions(w, extProfile);

    w.startRepeatingElement();
    for (E entry: entries) {
      entry.generateRss(w, extProfile);
    }
    w.endRepeatingElement();

    w.endElement(Namespaces.rssNs, "channel");
    w.endElement(Namespaces.rssNs, "rss");
  }


  /** Top-level namespace declarations for generated XML. */
  private static final Collection<XmlWriter.Namespace> namespaceDeclsAtom =
    new Vector<XmlWriter.Namespace>(2);

  private static final Collection<XmlWriter.Namespace> namespaceDeclsRss =
    new Vector<XmlWriter.Namespace>(2);

  private static final Collection<XmlWriter.Attribute> rssHeaderAttrs =
    new Vector<XmlWriter.Attribute>(1);

  static {

    namespaceDeclsAtom.add(Namespaces.atomNs);
    namespaceDeclsAtom.add(Namespaces.openSearchNs);

    namespaceDeclsRss.add(Namespaces.atomNs);
    namespaceDeclsRss.add(Namespaces.openSearchNs);

    rssHeaderAttrs.add(new XmlWriter.Attribute("version", "2.0"));
  }


  /**
   * Parses XML in the Atom format.
   *
   * @param   extProfile
   *            Extension profile.
   *
   * @param   input
   *            XML input stream.
   */
  public void parseAtom(ExtensionProfile extProfile,
                        InputStream input) throws IOException,
                                              ParseException {

    FeedHandler handler = new FeedHandler(extProfile);
    new XmlParser().parse(input, handler, Namespaces.atom, "feed");
  }

  /**
   * Parses XML in the Atom format.
   *
   * @param   extProfile
   *            Extension profile.
   *
   * @param   reader
   *            XML Reader.  The caller is responsible for ensuring that
   *            the character encoding is correct.
   */
  public void parseAtom(ExtensionProfile extProfile,
                        Reader reader) throws IOException,
                                              ParseException {

    FeedHandler handler = new FeedHandler(extProfile);
    new XmlParser().parse(reader, handler, Namespaces.atom, "feed");
  }


  /** {@code <atom:feed>} parser. */
  public class FeedHandler extends SourceHandler {


    public FeedHandler(ExtensionProfile extProfile) throws IOException {
      super(extProfile, Feed.class);
    }


    public ElementHandler getChildHandler(String namespace,
                                          String localName,
                                          Attributes attrs)
        throws ParseException, IOException {

      // Try ExtensionPoint. It returns {@code null} if there's no handler.
      ElementHandler extensionHandler =
        getExtensionHandler(extProfile, Feed.class,
                            namespace, localName, attrs);
      if (extensionHandler != null) {
        return extensionHandler;
      }

      if (namespace.equals(Namespaces.atom)) {

        if (localName.equals("entry")) {

          E entry = createEntry();
          entries.add(entry);
          return (ElementHandler)((BaseEntry)entry).new AtomHandler(extProfile);
        }

        // All other elements in the Atom namespace are handled by
        // the SourceHandler superclass
        return super.getChildHandler(namespace, localName, attrs);

      } else if (namespace.equals(Namespaces.openSearch)) {

        if (localName.equals("totalResults")) {
          return new TotalResultsHandler();
        } else if (localName.equals("startIndex")) {
          return new StartIndexHandler();
        } else if (localName.equals("itemsPerPage")) {
          return new ItemsPerPageHandler();
        }
      } else {

        return super.getChildHandler(namespace, localName, attrs);
      }

      return null;
    }


    /** {@code <opensearch:totalResults>} parser. */
    private class TotalResultsHandler extends ElementHandler {

      public void processEndElement() throws ParseException {

        if (feedState.totalResults != Query.UNDEFINED) {
          throw new ParseException("Duplicate totalResults.");
        }

        if (value == null) {
          throw new ParseException("logo must have a value.");
        }

        try {
          feedState.totalResults = Integer.valueOf(value).intValue();
        } catch (NumberFormatException e) {
          throw new ParseException("totalResults is not an integer");
        }
      }
    }


    /** {@code <opensearch:startIndex>} parser. */
    private class StartIndexHandler extends ElementHandler {

      public void processEndElement() throws ParseException {

        if (feedState.startIndex != Query.UNDEFINED) {
          throw new ParseException("Duplicate startIndex.");
        }

        if (value == null) {
          throw new ParseException("logo must have a value.");
        }

        try {
          feedState.startIndex = Integer.valueOf(value).intValue();
        } catch (NumberFormatException e) {
          throw new ParseException("startIndex must be an integer");
        }
      }
    }


    /** {@code <opensearch:itemsPerPage>} parser. */
    private class ItemsPerPageHandler extends ElementHandler {

      public void processEndElement() throws ParseException {

        if (feedState.itemsPerPage != Query.UNDEFINED) {
          throw new ParseException("Duplicate itemsPerPage.");
        }

        if (value == null) {
          throw new ParseException("logo must have a value.");
        }

        try {
          feedState.itemsPerPage = Integer.valueOf(value).intValue();
        } catch (NumberFormatException e) {
          throw new ParseException("itemsPerPage is not an integer");
        }
      }
    }

    public void processEndElement() throws ParseException {

      // Set the canPost flag based upon the presence of an entry post
      // link relation in the parsed feed.
      feedState.canPost = getEntryPostLink() != null;
    }
  }

  /**
   * Locates and returns the most specific {@link Kind.Adaptor} feed
   * subtype for this feed.  If none can be found for the current class,
   * {@code null} will be returned.
   */
  public BaseFeed<?,?> getAdaptedFeed() throws Kind.AdaptorException {

    BaseFeed adaptedFeed = null;

    // Find the BaseFeed adaptor instance that is most specific.
    for (Kind.Adaptor adaptor : getAdaptors()) {
      if (! (adaptor instanceof BaseFeed)) {
        continue;
      }
      // if first matching adaptor or a narrower subtype of the current one,
      // then use it.
      if (adaptedFeed == null ||
          adaptedFeed.getClass().isAssignableFrom(adaptor.getClass())) {
        adaptedFeed = (BaseFeed)adaptor;
      }
    }

    // If an adapted feed was found, then also synchronize the current set
    // of entries into it, adapting them as well.
    if (adaptedFeed != null) {
      List<E> sourceEntries;
      if (adaptedFeed != this) {
        sourceEntries = entries;
      } else {
        // Copy before clearing
        sourceEntries = new ArrayList<E>();
        sourceEntries.addAll(entries);
      }
      adaptedFeed.getEntries().clear();
      for (BaseEntry entry: sourceEntries) {
        adaptedFeed.getEntries().add(entry.getAdaptedEntry());
      }
    }
    return adaptedFeed;
  }

  /**
   * Gets a list of entries of a particular kind.
   */
  public <T extends BaseEntry> List<T> getEntries(Class<T> entryClass) {
    List<T> adaptedEntries = new ArrayList<T>();

    for (BaseEntry<?> entry : getEntries()) {
      T adapted = entry.getAdaptor(entryClass);
      if (adapted != null) {
        adaptedEntries.add(adapted);
      }
    }

    return adaptedEntries;
  }

}