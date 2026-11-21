import com.example.juke.desktop.CloseAction
import com.example.juke.desktop.closeAction
import kotlin.test.Test
import kotlin.test.assertEquals

class CloseActionTest {
    @Test fun closingWhilePlayingKeepsTheMusicGoing() = assertEquals(CloseAction.HideKeepPlaying, closeAction(playing = true))
    @Test fun closingInSilenceQuits() = assertEquals(CloseAction.Quit, closeAction(playing = false))
}
