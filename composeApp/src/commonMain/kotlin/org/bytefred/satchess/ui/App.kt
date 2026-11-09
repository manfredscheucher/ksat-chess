package org.bytefred.satchess.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.bytefred.satchess.ui.model.creditLine
import org.bytefred.satchess.ui.platform.listenForShareLinks
import org.bytefred.satchess.ui.platform.sharedPuzzleJson

/** Width from which the automatic layout puts panels side by side. */
private val WIDE_LAYOUT = 900.dp

/** Width from which the results get their own (third) panel. */
private val THREE_COLUMNS = 1200.dp

/**
 * The app root (doc/spec.md section 7): menu bar, then panels side by side with draggable dividers,
 * or all sections stacked with a sticky Solve bar. View -> Layout overrides the choice by width.
 */
@Composable
fun App(vm: PuzzleViewModel = viewModel { PuzzleViewModel() }) {
    SatChessTheme {
      val examples = remember { loadExamples() }
      var showExamples by remember { mutableStateOf(false) }
      // outlives the dialog, so it reopens as it was left
      val examplesState = remember { ExamplesTreeState() }
      val snackbar = remember { SnackbarHostState() }
      LaunchedEffect(Unit) {
          vm.start(sharedPuzzleJson(), examples)
          listenForShareLinks { json -> vm.leave { vm.openShareLink(json) } }
      }
      LaunchedEffect(vm.message) {
          vm.message?.let {
              snackbar.showSnackbar(it)
              vm.showMessage(null)
          }
      }

      BoxWithConstraints(Modifier.fillMaxSize()) {
          val stacked = when (vm.layoutMode) {
              LayoutMode.AUTO -> maxWidth < WIDE_LAYOUT
              LayoutMode.PORTRAIT -> true
              LayoutMode.LANDSCAPE -> false
          }
          // Replay keys bubble up here from any focused element that does not use them (text fields do).
          val replayKeys = Modifier.onKeyEvent { event ->
              event.type == KeyEventType.KeyDown && vm.replayKey(event.key)
          }
          Scaffold(
              modifier = replayKeys,
              topBar = { MenuBar(vm, onExamples = { showExamples = true }) },
              bottomBar = {
                  if (stacked) {
                      SolveBar(vm, Modifier.padding(8.dp))
                  }
              },
              snackbarHost = { SnackbarHost(snackbar) },
              containerColor = MaterialTheme.colorScheme.background,
          ) { padding ->
              if (stacked) {
                  StackedLayout(vm, Modifier.padding(padding))
              } else {
                  SideBySideLayout(vm, threeColumns = maxWidth >= THREE_COLUMNS, Modifier.padding(padding))
              }
          }
      }

      if (showExamples) {
          ExamplesDialog(
              examples = examples,
              state = examplesState,
              onLoad = { puzzle ->
                  showExamples = false
                  vm.leave { vm.load(puzzle) }
              },
              onDismiss = { showExamples = false },
          )
      }
    }
}

/**
 * Left: what is asked (header, question, start, advanced). Middle: timeline and board. Right (from
 * [THREE_COLUMNS] on): Solve and the results; with two columns they follow the board.
 */
@Composable
private fun SideBySideLayout(vm: PuzzleViewModel, threeColumns: Boolean, modifier: Modifier) {
    val panels = buildList {
        add(SplitPanel("question") { m ->
            ScrollPanel(m) {
                PuzzleHeader(vm, compact = false)
                QuestionSection(vm)
                StartSection(vm)
                AdvancedSection(vm)
            }
        })
        add(SplitPanel("board") { m ->
            ScrollPanel(m) {
                TimelineSection(vm)
                BoardEditorSection(vm)
                if (!threeColumns) {
                    SolveBar(vm)
                    ResultsSection(vm)
                }
            }
        })
        if (threeColumns) {
            add(SplitPanel("results") { m ->
                ScrollPanel(m) {
                    SolveBar(vm)
                    if (vm.run.puzzle == null) {
                        Section("Results") { Text("Solutions appear here, with a replay board.") }
                    }
                    ResultsSection(vm)
                }
            })
        }
    }
    SplitPane(
        panels = panels,
        weights = vm.panelWeights,
        onWeightsChange = { vm.panelWeights = it },
        horizontal = true,
        modifier = modifier.fillMaxSize().padding(horizontal = 4.dp),
    )
}

/** A panel's content: a padded column with a draggable scrollbar. */
@Composable
private fun ScrollPanel(modifier: Modifier, content: @Composable () -> Unit) {
    val vScroll = rememberScrollState()
    ScrollbarBox(vScroll, modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(vScroll).padding(vertical = 16.dp).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** No tabs: the sections one above the other. Solve scrolls down to the results. */
@Composable
private fun StackedLayout(vm: PuzzleViewModel, modifier: Modifier) {
    val vScroll = rememberScrollState()
    var resultsTop by remember { mutableStateOf(0) }
    // again when the first result arrives: until then there may not be enough content to scroll that far
    LaunchedEffect(vm.run.puzzle, vm.solutions.isNotEmpty() || vm.run.done != null) {
        if (vm.run.puzzle != null) {
            withFrameNanos { }
            vScroll.animateScrollTo(resultsTop)
        }
    }
    ScrollbarBox(vScroll, modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(vScroll).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PuzzleHeader(vm, compact = true)
            QuestionSection(vm, collapsible = true)
            StartSection(vm)
            TimelineSection(vm)
            BoardEditorSection(vm)
            AdvancedSection(vm)
            Box(Modifier.onGloballyPositioned { resultsTop = it.positionInParent().y.toInt() }) { ResultsSection(vm) }
        }
    }
}

/**
 * Title, composer / source and our description of the loaded puzzle. [compact] (phone) shows two
 * lines of the description; a click expands it.
 */
@Composable
private fun PuzzleHeader(vm: PuzzleViewModel, compact: Boolean) {
    val p = vm.puzzle
    var expanded by remember(p) { mutableStateOf(!compact) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(p.title.ifEmpty { "Untitled puzzle" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("puzzleTitle"))
        val meta = creditLine(p)
        val url = p.source?.url
        if (meta.isNotEmpty() || url != null) {
            val uriHandler = LocalUriHandler.current
            val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
            val text = buildAnnotatedString {
                append(meta)
                if (url != null) {
                    if (meta.isNotEmpty()) append(" · ")
                    withLink(LinkAnnotation.Clickable("source", linkStyle) { openUriSafely(uriHandler, url) }) { append("Source") }
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("puzzleSource"))
        }
        p.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}
