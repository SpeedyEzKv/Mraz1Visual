package dev.umbra.core;

import net.minecraft.world.entity.Entity;

/** Игрок ударил сущность (наш собственный удар — только реакция, ничего не отправляем). */
public interface AttackListener {
    void onAttack(Entity target);
}
