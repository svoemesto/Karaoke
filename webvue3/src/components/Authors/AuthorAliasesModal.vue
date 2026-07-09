<template>
  <transition name="modal-fade">
    <div class="aam-modal-backdrop">
      <div class="aam-area">

        <div class="aam-area-modal-header">
          Алиасы автора: {{ author.author }}
        </div>
        <div class="aam-area-modal-body">
          <p class="aam-hint">
            Алиасы — имена солистов/участников группы, по которым публичный поиск тоже находит
            этого автора (напр. «Виктор Цой» для «КИНО»).
          </p>
          <div v-if="!aliasesList.length" class="aam-empty">Алиасов пока нет.</div>
          <div v-for="(alias, idx) in aliasesList" :key="idx" class="aam-alias-row">
            <input class="aam-alias-input" v-model="aliasesList[idx]" placeholder="Имя солиста/участника">
            <button class="aam-btn-remove" @click="removeAlias(idx)" title="Удалить">×</button>
          </div>
          <button class="aam-btn-add" @click="addAlias">+ Добавить алиас</button>
        </div>
        <div class="aam-area-modal-footer">
          <button type="button" class="aam-btn-save" :disabled="saving" @click="save">Сохранить</button>
          <button type="button" class="aam-btn-close" @click="close">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: "AuthorAliasesModal",
  props: {
    authorId: {
      type: [Number, String],
      required: true
    }
  },
  data() {
    return {
      aliasesList: [],
      saving: false
    }
  },
  computed: {
    author() {
      return (this.$store.getters.getAuthorsDigest || []).find(a => a.id === this.authorId) || {};
    }
  },
  created() {
    this.aliasesList = (this.author.aliases || '')
        .split(';')
        .map(a => a.trim())
        .filter(a => a.length > 0);
  },
  methods: {
    addAlias() {
      this.aliasesList.push('');
    },
    removeAlias(idx) {
      this.aliasesList.splice(idx, 1);
    },
    save() {
      const cleaned = this.aliasesList.map(a => a.trim()).filter(a => a.length > 0);
      const payload = {
        id: this.author.id,
        author: this.author.author,
        ymId: this.author.ymId,
        vkId: this.author.vkId,
        lastAlbumYm: this.author.lastAlbumYm,
        lastAlbumVk: this.author.lastAlbumVk,
        lastAlbumProcessed: this.author.lastAlbumProcessed,
        watched: this.author.watched,
        skip: this.author.skip,
        aliases: cleaned.join(';')
      };
      this.saving = true;
      this.$store.dispatch('setAuthorValuePromise', payload)
          .then(() => {
            this.$store.dispatch('loadOneRecord', this.authorId);
            this.saving = false;
            this.close();
          })
          .catch(error => {
            console.error("Ошибка при сохранении алиасов:", error);
            this.saving = false;
          });
    },
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.aam-modal-fade-enter,
.aam-modal-fade-leave-active {
  opacity: 0;
}

.aam-modal-fade-enter-active,
.aam-modal-fade-leave-active {
  transition: opacity .5s ease
}

.aam-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.aam-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
  min-width: 400px;
}

.aam-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.aam-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.aam-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}

.aam-hint {
  font-size: small;
  color: dimgray;
  margin: 0 0 10px 0;
}

.aam-empty {
  font-size: small;
  color: gray;
  margin-bottom: 10px;
}

.aam-alias-row {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 5px;
}

.aam-alias-input {
  width: 350px;
  box-sizing: border-box;
  border: 1px solid gray;
  border-radius: 5px;
  padding: 4px 6px;
}

.aam-btn-remove {
  border: solid 1px black;
  border-radius: 5px;
  width: 24px;
  height: 24px;
  line-height: 1;
  background-color: antiquewhite;
  cursor: pointer;
}
.aam-btn-remove:hover { background-color: lightpink; }

.aam-btn-add {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  margin-top: 5px;
  background-color: antiquewhite;
  cursor: pointer;
}
.aam-btn-add:hover { background-color: lightpink; }

.aam-btn-save {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 18px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: transparent;
  width: 150px;
  height: auto;
}
.aam-btn-save:hover { background: darkgreen; }
.aam-btn-save[disabled] { opacity: 0.5; cursor: default; }

.aam-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
  height: auto;
}

</style>
