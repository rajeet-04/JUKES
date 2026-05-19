package com.example.juke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val titleWidths = listOf(0.72f, 0.58f, 0.81f, 0.66f, 0.76f, 0.63f)
private val subtitleWidths = listOf(0.36f, 0.49f, 0.42f, 0.31f, 0.53f, 0.39f)

@Composable
private fun ShapedSkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

@Composable
private fun TrackRowSkeleton(index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShapedSkeletonBlock(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShapedSkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(titleWidths[index % titleWidths.size])
                    .height(18.dp),
                shape = RoundedCornerShape(6.dp)
            )
            ShapedSkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(subtitleWidths[index % subtitleWidths.size])
                    .height(12.dp),
                shape = RoundedCornerShape(6.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        ShapedSkeletonBlock(
            modifier = Modifier.size(18.dp),
            shape = CircleShape
        )
    }
}

@Composable
fun TrackListSkeleton(
    count: Int = 8,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(top = 16.dp, bottom = 100.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(count) { index ->
            TrackRowSkeleton(index = index)
        }
    }
}

@Composable
fun MediaDetailSkeleton(
    count: Int = 6,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = 20.dp,
        top = 16.dp,
        end = 20.dp,
        bottom = 16.dp
    )
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShapedSkeletonBlock(
                    modifier = Modifier.size(200.dp),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                ShapedSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(28.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                ShapedSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(18.dp),
                    shape = RoundedCornerShape(6.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        ShapedSkeletonBlock(
                            modifier = Modifier
                                .width(72.dp)
                                .height(28.dp),
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
        }

        items(count) { index ->
            TrackRowSkeleton(index = index)
        }
    }
}

@Composable
fun PlayerSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShapedSkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(28.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        ShapedSkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .height(30.dp),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        ShapedSkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(18.dp),
            shape = RoundedCornerShape(6.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        ShapedSkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            shape = CircleShape
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShapedSkeletonBlock(
                modifier = Modifier
                    .width(42.dp)
                    .height(12.dp),
                shape = RoundedCornerShape(4.dp)
            )
            ShapedSkeletonBlock(
                modifier = Modifier
                    .width(42.dp)
                    .height(12.dp),
                shape = RoundedCornerShape(4.dp)
            )
        }
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapedSkeletonBlock(
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            )
            ShapedSkeletonBlock(
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            )
            ShapedSkeletonBlock(
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            )
        }
    }
}

@Composable
fun HomeSkeleton(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 0.dp,
            end = 24.dp,
            bottom = 24.dp + bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShapedSkeletonBlock(
                        modifier = Modifier
                            .width(56.dp)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                    ShapedSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.64f)
                            .height(34.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                ShapedSkeletonBlock(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                )
            }
        }

        item {
            ShapedSkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(32.dp)
            )
        }

        item { HomeSkeletonSection() }
        item { HomeSkeletonSection() }
    }
}

@Composable
private fun HomeSkeletonSection() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapedSkeletonBlock(
                modifier = Modifier
                    .width(132.dp)
                    .height(18.dp),
                shape = RoundedCornerShape(6.dp)
            )
            ShapedSkeletonBlock(
                modifier = Modifier
                    .width(56.dp)
                    .height(14.dp),
                shape = RoundedCornerShape(6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShapedSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.92f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    ShapedSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .height(14.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                    ShapedSkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.56f)
                            .height(12.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                }
            }
        }
    }
}