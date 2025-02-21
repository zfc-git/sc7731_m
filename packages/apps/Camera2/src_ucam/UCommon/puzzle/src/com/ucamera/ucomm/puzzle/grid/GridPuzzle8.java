/*
 * Copyright (C) 2011,2012 Thundersoft Corporation
 * All rights Reserved
 */

package com.ucamera.ucomm.puzzle.grid;

import com.ucamera.ucomm.puzzle.PuzzleSpec;

public class GridPuzzle8 extends GridBase {

    public GridPuzzle8() {
        mSpec = PuzzleSpec.create(8);
    }

    /* MAYBE ROTATED
     *  +---+---+---+
     *  |   |   |   |
     *  +---+   +---+
     *  |   +---+   |
     *  +---+   +---+
     *  |   |   |   |
     *  +---+---+---+ (6,6)
     */
    @PuzzleMethod(Type.GRID)
    public void random1() {
        float points[] = {
                0, 0, 2, 2,
                0, 2, 2, 4,
                0, 4, 2, 6,
                2, 0, 4, 3,
                2, 3, 4, 6,
                4, 0, 6, 2,
                4, 2, 6, 4,
                4, 4, 6, 6
        };
        randomRotate(points, 6, 6);
        setupSpec(points);
    }

    /* MAYBE rotated
     * +---+-------+---+
     * |   |       |   |
     * |   +-------+   |
     * |   |       |   |
     * +---+-------+---+
     * |   |       |   |
     * |   +-------+   |
     * |   |       |   |
     * +---+-------+---+ (4,4)
     */
    @PuzzleMethod(Type.GRID)
    public void random2(){
        float points[] = {
                0, 0, 1, 2,
                0, 2, 1, 4,
                1, 3, 3, 4,
                3, 2, 4, 4,
                3, 0, 4, 2,
                1, 0, 3, 1,
                1, 1, 3, 2,
                1, 2, 3, 3
        };
        randomRotate(points, 4, 4);
        setupSpec(points);
    }


   /* MAYBE rotated
    * +---+-----------+
    * |   |           |
    * +---+           |
    * |   |           |
    * +---+           |
    * |   |           |
    * +---+---+---+---+
    * |   |   |   |   |
    * +---+---+---+---+ (4,4)
    */
    @PuzzleMethod(Type.GRID)
    public void random3() {
        float points[] = {
                0, 0, 1, 1,
                0, 1, 1, 2,
                0, 2, 1, 3,
                0, 3, 1, 4,
                1, 3, 2, 4,
                2, 3, 3, 4,
                3, 3, 4, 4,
                1, 0, 4, 3
        };
        randomRotate(points, 4, 4);
        setupSpec(points);
    }

    /* MAYBE rotated
     * +---+---+-------+
     * |   |   |       |
     * |   |   +-------+
     * |   |   |       |
     * +---+---+-------+
     * |   |   |       |
     * |   |   +-------+
     * |   |   |       |
     * +---+---+-------+ (4,4)
     */
    @PuzzleMethod(Type.GRID)
    public void random4(){
        float points[] = {
                0, 0, 1, 2,
                0, 2, 1, 4,
                1, 0, 2, 2,
                1, 2, 2, 4,
                2, 0, 4, 1,
                2, 1, 4, 2,
                2, 2, 4, 3,
                2, 3, 4, 4
        };
        randomRotate(points, 4, 4);
        setupSpec(points);
    }

    /* MAYBE ROTATED
     *  +---+---+---+
     *  |   |   |   |
     *  +---+---+   +
     *  |   |   +---+
     *  +---+---+   +
     *  |   |   |   |
     *  +---+---+---+ (6,6)
     */
    @PuzzleMethod(Type.GRID)
    public void random5() {
        float points[] = {
                0, 0, 2, 2,
                0, 2, 2, 4,
                0, 4, 2, 6,
                2, 0, 4, 2,
                2, 2, 4, 4,
                2, 4, 4, 6,
                4, 0, 6, 3,
                4, 3, 6, 6
        };
        randomRotate(points, 6, 6);
        setupSpec(points);
    }

    /* MAYBE rotated
     * +---+---+-------+
     * |   |   |       |
     * |   |   |       |
     * +---+---+-------+
     * |   |   |       |
     * |   |   |       |
     * +---+---+       |
     * |   |   |       |
     * |   |   |       |
     * +---+---+-------+ (4,3)
     */
    @PuzzleMethod(Type.GRID)
    public void random6(){
        float points[] = {
                0, 0, 1, 1,
                0, 1, 1, 2,
                0, 2, 1, 3,
                1, 0, 2, 1,
                1, 1, 2, 2,
                1, 2, 2, 3,
                2, 0, 4, 1,
                2, 1, 4, 3
        };
        randomRotate(points, 4, 3);
        setupSpec(points);
    }

    /* MAYBE rotated
     * +-------+---+---+
     * |       |   |   |
     * |       +---+---+
     * |       |   |   |
     * +---+---+---+---+
     * |   |   |       |
     * |   |   |       |
     * |   |   |       |
     * +---+---+-------+ (4,4)
     */
    @PuzzleMethod(Type.GRID)
    public void random7(){
        float points[] = {
                0, 0, 2, 2,
                0, 2, 1, 4,
                1, 2, 2, 4,
                2, 0, 3, 1,
                2, 1, 3, 2,
                3, 0, 4, 1,
                3, 1, 4, 2,
                2, 2, 4, 4
        };
        randomRotate(points, 4, 4);
        setupSpec(points);
    }

    /* MAYBE rotated
     * +---+---+---+---+
     * |   |   |   |   |
     * |   |   |   |   |
     * |   |   |   |   |
     * +---+---+---+---+
     * |   |   |   |   |
     * |   |   |   |   |
     * |   |   |   |   |
     * +---+---+---+---+ (4,4)
     */
    @PuzzleMethod(Type.GRID)
    public void random8(){
        float points[] = {
                0, 0, 1, 2,
                0, 2, 1, 4,
                1, 0, 2, 2,
                1, 2, 2, 4,
                2, 0, 3, 2,
                2, 2, 3, 4,
                3, 0, 4, 2,
                3, 2, 4, 4
        };
        randomRotate(points, 4, 4);
        setupSpec(points);
    }
}
