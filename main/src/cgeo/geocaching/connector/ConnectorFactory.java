package cgeo.geocaching.connector;

import cgeo.geocaching.ICache;
import cgeo.geocaching.R;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.Trackable;
import cgeo.geocaching.connector.capability.ILogin;
import cgeo.geocaching.connector.capability.ISearchByCenter;
import cgeo.geocaching.connector.capability.ISearchByFinder;
import cgeo.geocaching.connector.capability.ISearchByKeyword;
import cgeo.geocaching.connector.capability.ISearchByOwner;
import cgeo.geocaching.connector.capability.ISearchByViewPort;
import cgeo.geocaching.connector.ec.ECConnector;
import cgeo.geocaching.connector.gc.GCConnector;
import cgeo.geocaching.connector.gc.MapTokens;
import cgeo.geocaching.connector.oc.OCApiConnector;
import cgeo.geocaching.connector.oc.OCApiConnector.ApiSupport;
import cgeo.geocaching.connector.oc.OCApiLiveConnector;
import cgeo.geocaching.connector.oc.OCConnector;
import cgeo.geocaching.connector.ox.OXConnector;
import cgeo.geocaching.connector.trackable.GeokretyConnector;
import cgeo.geocaching.connector.trackable.TrackableConnector;
import cgeo.geocaching.connector.trackable.TravelBugConnector;
import cgeo.geocaching.connector.trackable.UnknownTrackableConnector;
import cgeo.geocaching.geopoint.Viewport;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ConnectorFactory {
    private static final @NonNull UnknownConnector UNKNOWN_CONNECTOR = new UnknownConnector();
    private static final Collection<IConnector> CONNECTORS = Collections.unmodifiableCollection(Arrays.asList(new IConnector[] {
            GCConnector.getInstance(),
            ECConnector.getInstance(),
            new OCApiLiveConnector("opencaching.de", "www.opencaching.de", "OC", "CC BY-NC-ND, alle Logeinträge © jeweiliger Autor",
                    R.string.oc_de_okapi_consumer_key, R.string.oc_de_okapi_consumer_secret,
                    R.string.pref_connectorOCActive, R.string.pref_ocde_tokenpublic, R.string.pref_ocde_tokensecret, ApiSupport.current),
            new OCConnector("OpenCaching.CZ", "www.opencaching.cz", "OZ"),
            new OCApiConnector("OpenCaching.CO.UK", "www.opencaching.org.uk", "OK", "arU4okouc4GEjMniE2fq", "CC BY-NC-SA 2.5", ApiSupport.oldapi),
            new OCConnector("OpenCaching.ES", "www.opencachingspain.es", "OC"),
            new OCConnector("OpenCaching.IT", "www.opencaching.it", "OC"),
            new OCConnector("OpenCaching.JP", "www.opencaching.jp", "OJ"),
            new OCConnector("OpenCaching.NO/SE", "www.opencaching.se", "OS"),
            new OCApiConnector("OpenCaching.NL", "www.opencaching.nl", "OB", "PdzU8jzIlcfMADXaYN8j", "CC BY-SA 3.0", ApiSupport.current),
            new OCApiLiveConnector("opencaching.pl", "www.opencaching.pl", "OP", "CC BY-SA 3.0",
                    R.string.oc_pl_okapi_consumer_key, R.string.oc_pl_okapi_consumer_secret,
                    R.string.pref_connectorOCPLActive, R.string.pref_ocpl_tokenpublic, R.string.pref_ocpl_tokensecret, ApiSupport.current),
            new OCApiConnector("OpenCaching.US", "www.opencaching.us", "OU", "pTsYAYSXFcfcRQnYE6uA", "CC BY-NC-SA 2.5", ApiSupport.current),
            new OXConnector(),
            new GeocachingAustraliaConnector(),
            new GeopeitusConnector(),
            new WaymarkingConnector(),
            UNKNOWN_CONNECTOR // the unknown connector MUST be the last one
    }));

    @NonNull public static final UnknownTrackableConnector UNKNOWN_TRACKABLE_CONNECTOR = new UnknownTrackableConnector();
    private static final Collection<TrackableConnector> TRACKABLE_CONNECTORS = Collections.unmodifiableCollection(Arrays.asList(new TrackableConnector[] {
            new GeokretyConnector(), // GK must be first, as it overlaps with the secret codes of travel bugs
            TravelBugConnector.getInstance(),
            UNKNOWN_TRACKABLE_CONNECTOR // must be last
    }));

    private static final Collection<ISearchByViewPort> searchByViewPortConns = getMatchingConnectors(ISearchByViewPort.class);

    private static final Collection<ISearchByCenter> searchByCenterConns = getMatchingConnectors(ISearchByCenter.class);

    private static final Collection<ISearchByKeyword> searchByKeywordConns = getMatchingConnectors(ISearchByKeyword.class);

    private static final Collection<ISearchByOwner> SEARCH_BY_OWNER_CONNECTORS = getMatchingConnectors(ISearchByOwner.class);

    private static final Collection<ISearchByFinder> SEARCH_BY_FINDER_CONNECTORS = getMatchingConnectors(ISearchByFinder.class);

    @SuppressWarnings("unchecked")
    private static <T extends IConnector> Collection<T> getMatchingConnectors(final Class<T> clazz) {
        final List<T> matching = new ArrayList<T>();
        for (final IConnector connector : CONNECTORS) {
            if (clazz.isInstance(connector)) {
                matching.add((T) connector);
            }
        }
        return Collections.unmodifiableCollection(matching);
    }

    public static Collection<IConnector> getConnectors() {
        return CONNECTORS;
    }

    public static Collection<ISearchByCenter> getSearchByCenterConnectors() {
        return searchByCenterConns;
    }

    public static Collection<ISearchByKeyword> getSearchByKeywordConnectors() {
        return searchByKeywordConns;
    }

    public static Collection<ISearchByOwner> getSearchByOwnerConnectors() {
        return SEARCH_BY_OWNER_CONNECTORS;
    }

    public static Collection<ISearchByFinder> getSearchByFinderConnectors() {
        return SEARCH_BY_FINDER_CONNECTORS;
    }

    public static ILogin[] getActiveLiveConnectors() {
        final List<ILogin> liveConns = new ArrayList<ILogin>();
        for (final IConnector conn : CONNECTORS) {
            if (conn instanceof ILogin && conn.isActive()) {
                liveConns.add((ILogin) conn);
            }
        }
        return liveConns.toArray(new ILogin[liveConns.size()]);
    }

    public static boolean canHandle(final @Nullable String geocode) {
        if (geocode == null) {
            return false;
        }
        if (isInvalidGeocode(geocode)) {
            return false;
        }
        for (final IConnector connector : CONNECTORS) {
            if (connector.canHandle(geocode)) {
                return true;
            }
        }
        return false;
    }

    public static @NonNull
    IConnector getConnector(ICache cache) {
        return getConnector(cache.getGeocode());
    }

    public static TrackableConnector getConnector(Trackable trackable) {
        return getTrackableConnector(trackable.getGeocode());
    }

    @NonNull
    public static TrackableConnector getTrackableConnector(String geocode) {
        for (final TrackableConnector connector : TRACKABLE_CONNECTORS) {
            if (connector.canHandleTrackable(geocode)) {
                return connector;
            }
        }
        return UNKNOWN_TRACKABLE_CONNECTOR; // avoid null checks by returning a non implementing connector
    }

    public static @NonNull
    IConnector getConnector(final String geocodeInput) {
        // this may come from user input
        final String geocode = StringUtils.trim(geocodeInput);
        if (geocode == null) {
            return UNKNOWN_CONNECTOR;
        }
        if (isInvalidGeocode(geocode)) {
            return UNKNOWN_CONNECTOR;
        }
        for (final IConnector connector : CONNECTORS) {
            if (connector.canHandle(geocode)) {
                return connector;
            }
        }
        // in case of errors, take UNKNOWN to avoid null checks everywhere
        return UNKNOWN_CONNECTOR;
    }

    private static boolean isInvalidGeocode(final String geocode) {
        return StringUtils.isBlank(geocode) || !Character.isLetterOrDigit(geocode.charAt(0));
    }

    /** @see ISearchByViewPort#searchByViewport */
    public static Observable<SearchResult> searchByViewport(final @NonNull Viewport viewport, final MapTokens tokens) {
        return Observable.from(searchByViewPortConns).filter(new Func1<ISearchByViewPort, Boolean>() {
            @Override
            public Boolean call(final ISearchByViewPort connector) {
                return connector.isActive();
            }
        }).parallel(new Func1<Observable<ISearchByViewPort>, Observable<SearchResult>>() {
            @Override
            public Observable<SearchResult> call(final Observable<ISearchByViewPort> connector) {
                return connector.map(new Func1<ISearchByViewPort, SearchResult>() {
                    @Override
                    public SearchResult call(final ISearchByViewPort connector) {
                        return connector.searchByViewport(viewport, tokens);
                    }
                });
            }
        }, Schedulers.io()).reduce(new SearchResult(), new Func2<SearchResult, SearchResult, SearchResult>() {

            @Override
            public SearchResult call(final SearchResult result, final SearchResult searchResult) {
                result.addSearchResult(searchResult);
                return result;
            }
        });
    }

    public static String getGeocodeFromURL(final String url) {
        for (final IConnector connector : CONNECTORS) {
            final String geocode = connector.getGeocodeFromUrl(url);
            if (StringUtils.isNotBlank(geocode)) {
                return geocode;
            }
        }
        return null;
    }

    public static Collection<TrackableConnector> getTrackableConnectors() {
        return TRACKABLE_CONNECTORS;
    }

    /**
     * Get the geocode of a trackable from a URL.
     *
     * @param url
     * @return {@code null} if the URL cannot be decoded
     */
    public static String getTrackableFromURL(final String url) {
        if (url == null) {
            return null;
        }
        for (final TrackableConnector connector : TRACKABLE_CONNECTORS) {
            final String geocode = connector.getTrackableCodeFromUrl(url);
            if (StringUtils.isNotBlank(geocode)) {
                return geocode;
            }
        }
        return null;
    }

}
