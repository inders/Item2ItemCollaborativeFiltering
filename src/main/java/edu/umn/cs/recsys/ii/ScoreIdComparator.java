package edu.umn.cs.recsys.ii;

import org.grouplens.lenskit.scored.ScoredId;

import java.util.Collections;
import java.util.Comparator;

/**
 * Created with IntelliJ IDEA.
 * User: inderbir.singh
 * Date: 11/11/13
 * Time: 7:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class ScoreIdComparator implements Comparator<ScoredId> {

    @Override
    public int compare(ScoredId o1, ScoredId o2) {
        if (o1.getScore() > o2.getScore())
            return 1;
        else if (o1.getScore() < o2.getScore())
            return -1;
        else
            return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
