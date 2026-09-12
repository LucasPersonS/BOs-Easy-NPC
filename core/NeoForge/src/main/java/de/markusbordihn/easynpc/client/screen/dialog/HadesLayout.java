package de.markusbordihn.easynpc.client.screen.dialog;

/** GUI-scaled coordinates shared by the screen and off-screen composition checks. */
record HadesLayout(int x, int y, int width, int height, int linesPerPage, boolean portraitVisible) {
  static HadesLayout fit(int screenWidth, int screenHeight) {
    int left = screenWidth >= 420 ? (int) (screenWidth * .39) : 18;
    int width = Math.min(540, screenWidth - left - 20);
    int lines = Math.max(2, Math.min(5, (screenHeight - 155) / 12));
    int height = 36 + lines * 12;
    return new HadesLayout(screenWidth - width - 20, Math.max(48, screenHeight - height - 78),
        width, height, lines, screenWidth >= 420);
  }

  static int lastPage(int lineCount, int linesPerPage) {
    return Math.max(0, (lineCount - 1) / Math.max(1, linesPerPage));
  }
}
