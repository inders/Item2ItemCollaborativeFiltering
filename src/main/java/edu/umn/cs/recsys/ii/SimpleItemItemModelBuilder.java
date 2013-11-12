package edu.umn.cs.recsys.ii;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdBuilder;
import org.grouplens.lenskit.scored.ScoredIdListBuilder;
import org.grouplens.lenskit.scored.ScoredIds;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemModelBuilder implements Provider<SimpleItemItemModel> {
    private final ItemDAO itemDao;
    private final UserEventDAO userEventDao;
    private static final Logger logger = LoggerFactory.getLogger(SimpleItemItemModelBuilder.class);;
    private final CosineVectorSimilarity cosineVectorSimilarity = new CosineVectorSimilarity();

    @Inject
    public SimpleItemItemModelBuilder(@Transient ItemDAO idao,
                                      @Transient UserEventDAO uedao) {
        itemDao = idao;
        userEventDao = uedao;
    }

    @Override
    public SimpleItemItemModel get() {
        // Get the transposed rating matrix
        // This gives us a map of item IDs to those items' rating vectors
        Map<Long, ImmutableSparseVector> itemVectors = getItemVectors();

        // Get all items - you might find this useful
        LongSortedSet items = LongUtils.packedSet(itemVectors.keySet());

        LongSortedSet items1 = LongUtils.packedSet(itemVectors.keySet());

        // Map items to vectors of item similarities
        //itemId to score with other items i.e. item to item simiiarity matrix
        Map<Long, List<ScoredId>> modelMap = new HashMap<Long, List<ScoredId>>();

        // TODO Compute the similarities between each pair of items
        // It will need to be in a map of longs to lists of Scored IDs to store in the model

        for (long item : items) {
            SparseVector itemRatingVector = itemVectors.get(item);
            for(long item1 :items1 ) {
                if (item != item1) {
                    //exclude same item similarity from the matrix
                    SparseVector item1RatingVector = itemVectors.get(item1);
                    double score = cosineVectorSimilarity.similarity(itemRatingVector, item1RatingVector);

                    if (score > 0) {
                        ScoredId scoredId = ScoredIds.create(item1, score);
                        List<ScoredId> scoredIdList = modelMap.get(item);
                        if (scoredIdList == null){
                            scoredIdList = new ArrayList<ScoredId>();
                            modelMap.put(item, scoredIdList);
                        }
                        scoredIdList.add(scoredId);
                    }
                }
            }
        }


        //  Ordering<ScoredId> scoredIdOrdering = ScoredIds.scoreOrder().reverse();
        //go over the map and sort each list of scoreIds
        for (Map.Entry entry : modelMap.entrySet()) {

            Comparator<ScoredId> scoredIdComparator =  Collections.reverseOrder(new ScoreIdComparator());
            Long item = (Long) entry.getKey();
            List<ScoredId> list = (List<ScoredId>) entry.getValue();
            Collections.sort(list, scoredIdComparator);

        }

        return new SimpleItemItemModel(modelMap);
    }

    /**
     * Load the data into memory, indexed by item.
     * @return A map from item IDs to item rating vectors. Each vector contains users' ratings for
     * the item, keyed by user ID.
     */
    public Map<Long,ImmutableSparseVector> getItemVectors() {
        // set up storage for building each item's rating vector
        LongSet items = itemDao.getItemIds();
        // map items to maps from users to ratings
        Map<Long,Map<Long,Double>> itemData = new HashMap<Long, Map<Long, Double>>();
        for (long item: items) {
            itemData.put(item, new HashMap<Long, Double>());
        }
        // itemData should now contain a map to accumulate the ratings of each item

        // stream over all user events
        Cursor<UserHistory<Event>> stream = userEventDao.streamEventsByUser();
        try {
            for (UserHistory<Event> evt: stream) {
                MutableSparseVector vector = RatingVectorUserHistorySummarizer.makeRatingVector(evt).mutableCopy();
                // vector is now the user's rating vector
                // TODO Normalize this vector and store the ratings in the item data

                //normalize the vector. P.S. - this is a user rating vector with dimension being items
                MutableSparseVector meanVector = MutableSparseVector.create(vector.keySet());
                meanVector.fill(vector.mean());
                vector.subtract(meanVector);

                long userId = evt.getUserId();

                //store ratings in itemsData.
                //iterate over user Rating Vector
                for (VectorEntry entry : vector.fast(VectorEntry.State.EITHER)) {
                    long itemId = entry.getKey();
                    double rating = entry.getValue();
                        Map<Long, Double> userRatingMap = itemData.get(itemId);
                        userRatingMap.put(userId, rating);
                }
            }
        } finally {
            stream.close();
        }

        // This loop converts our temporary item storage to a map of item vectors
        Map<Long,ImmutableSparseVector> itemVectors = new HashMap<Long, ImmutableSparseVector>();
        for (Map.Entry<Long,Map<Long,Double>> entry: itemData.entrySet()) {
            MutableSparseVector vec = MutableSparseVector.create(entry.getValue());
            itemVectors.put(entry.getKey(), vec.immutable());
        }
        return itemVectors;
    }
}
