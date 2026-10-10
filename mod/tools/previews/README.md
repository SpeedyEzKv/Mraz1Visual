# Генератор превью («Minecraft с шейдерами»)

Свой воксельный мир и текстуры; трава-спрайты, мягкие тени, затенение углов, небо с облаками,
лучи солнца, свечение фонарей, ACES-тонмаппинг, цветокоррекция. Скины — стандартная развёртка 64×64
(с внешним слоем): `skins/player.png` — твой персонаж.

    pip install numpy pillow
    python scenes3.py out                 # все сцены (~25 минут)
    python scenes3.py out duel,front      # только выбранные
    # затем out/*.png → src/main/resources/assets/umbra/textures/preview/
    # и out/scenes.json → src/main/resources/assets/umbra/previews/

Сменить скин: положи свой PNG в skins/player.png (или задай UMBRA_SKIN=имя.png) и перегенерируй.
Сцены: duel (замок ночью, удар), night (замок), front (прыжок, лес), back (ходьба, закат),
fp (взмах от первого лица), blocks (установка блоков).
