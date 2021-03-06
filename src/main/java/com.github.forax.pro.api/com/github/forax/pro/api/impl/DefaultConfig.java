package com.github.forax.pro.api.impl;

import java.util.Optional;

import com.github.forax.pro.api.Config;
import com.github.forax.pro.api.MutableConfig;
import com.github.forax.pro.api.impl.Configs.DuplicatableConfig;
import com.github.forax.pro.api.impl.Configs.EvalContext;

public class DefaultConfig implements DuplicatableConfig, EvalContext {
  private final Object root;
  
  public DefaultConfig() {
    this.root = Configs.newRoot(this);
  }

  private DefaultConfig(Object root) {  // warning duplicate the tree, use with care
    this.root = Configs.duplicate(root, this);
  }
  
  @Override
  public String toString() {
    return root.toString();
  }
  
  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    return Configs.get(root, key, type, true);
  }
  
  @Override
  public <T> T getOrUpdate(String key, Class<T> type) {
    return Configs.get(root, key, type, false).get();
  }
  
  @Override
  public void set(String key, Object value) {
    Configs.set(root, key, value);
  }
  
  //@Override
  //public void forEach(String key, BiConsumer<? super String, Object> consumer) {
  //   Configs.forEach(root, key, consumer); 
  //}
  
  @Override
  public DuplicatableConfig duplicate() {
    return new DefaultConfig(root);
  }
  
  public static DuplicatableConfig asNonMutable(Config config) {
    return new DuplicatableConfig() {
      @Override
      public <T> Optional<T> get(String key, Class<T> type) {
        return config.get(key, type);
      }

      @Override
      public DuplicatableConfig duplicate() {
        return this;
      }

      @Override
      public Config asConfig() {
        return config;
      }
      
      @Override
      public <T> T getOrUpdate(String key, Class<T> type) {
        throw new UnsupportedOperationException("getOrUpdate() can not be used on a non-mutable configuration");
      }

      @Override
      public void set(String key, Object value) {
        throw new UnsupportedOperationException("set() can not be used on a non-mutable configuration");
      }
    };
  }
  
  /*
  public void enter(Runnable runnable) {
    var oldRoot = root;
    var newRoot = Configs.duplicate(oldRoot);
    root = newRoot;
    try {
      runnable.run();
    } finally {
      root = oldRoot;
    }
  }*/
  
  public MutableConfig asChecked(String prefix) {
    return new MutableConfig() {
      @Override
      public <T> T getOrUpdate(String key, Class<T> type) {
        if (!key.startsWith(prefix + ".") && !key.equals(prefix)) {
          throw new IllegalArgumentException("a plugin can only set a key starting by its name");
        }
        return DefaultConfig.this.getOrUpdate(key, type);
      }
      
      @Override
      public <T> Optional<T> get(String key, Class<T> type) {
        return DefaultConfig.this.get(key, type);
      }
      
      @Override
      public void set(String key, Object object) {
        if (!key.startsWith(prefix + ".")) {
          throw new IllegalArgumentException("a plugin can only set a key starting by its name");
        }
        DefaultConfig.this.set(key, object);
      }
      
      //@Override
      //public void forEach(String key, BiConsumer<? super String, Object> consumer) {
      //  DefaultConfig.this.forEach(key, consumer);
      //}
      
      @Override
      public String toString() {
        return DefaultConfig.this.toString();
      }
    };
  }
  
  @Override
  public Config asConfig() {
    return new Config() {
      @Override
      public <T> Optional<T> get(String key, Class<T> type) {
        return DefaultConfig.this.get(key, type);
      }
      
      //@Override
      //public void forEach(String key, BiConsumer<? super String, Object> consumer) {
      //  DefaultConfig.this.forEach(key, consumer);
      //}
      
      @Override
      public String toString() {
        return DefaultConfig.this.toString();
      }
    };
  }
}
