package edu.umn.cs.recsys.ii;

import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdListBuilder;
import org.grouplens.lenskit.scored.ScoredIds;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemScorer extends AbstractItemScorer {
    private final SimpleItemItemModel model;
    private final UserEventDAO userEvents;
    private final int neighborhoodSize;

    @Inject
    public SimpleItemItemScorer(SimpleItemItemModel m, UserEventDAO dao,
                                @NeighborhoodSize int nnbrs) {
        model = m;
        userEvents = dao;
        neighborhoodSize = nnbrs;
    }

    /**
     * Score items for a user.
     * @param user The user ID.
     * @param scores The score vector.  Its key domain is the items to score, and the scores
     *               (rating predictions) should be written back to this vector.
     */
    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector ratings = getUserRatingVector(user);

     /*   System.out.println("Ratings by user " + user +" are on items");
        System.out.println(ratings.keySet());
*/
        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
            long item = e.getKey();

            List<ScoredId> neighbors = model.getNeighbors(item);
            // TODO Score this item and save the score into scores
       //     System.out.println("Neighbours for item " + item + " size " + neighbors.size());
         //   System.out.println(neighbors);

            double score = scoreItem(ratings, neighbors);
            scores.set(item, score);
        }
    }


    /**
     * scoring is done using a weighted average of users rating and similarity of items being the weight
     * @param ratings
     * @param neighbours
     * @return
     */
    private double scoreItem(SparseVector ratings, List<ScoredId> neighbours) {
        double numerator = 0, denominator = 0;
        int processedNeighbours = 0;

     //   System.out.println("Total neighbour hood size" + neighborhoodSize);
        for (ScoredId neighbourItem : neighbours) {
            long item = neighbourItem.getId();
            if (ratings.containsKey(item)) {
                double similarityScore = neighbourItem.getScore();
                double rating = ratings.get(item);
                numerator +=  rating * similarityScore ;  //rating * similarityScore
       //         System.out.println("Item " + item + "rating " + rating + " score " + similarityScore);
                denominator += similarityScore;
                processedNeighbours++;
                if (processedNeighbours == neighborhoodSize)
                    break;
            }
        }
       // System.out.println("Total processed neighbours " + processedNeighbours);
        double finalScore = numerator / denominator; //since we have only positive scores doing Absolute in denominator is optional
        return finalScore;
    }


    /**
     * Get a user's ratings.
     * @param user The user ID.
     * @return The ratings to retrieve.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userEvents.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }

        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
}
