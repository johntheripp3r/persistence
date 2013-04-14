package com.codeslap.persistence.processor;

import com.codeslap.persistence.Ignore;
import com.codeslap.persistence.PrimaryKey;
import com.codeslap.persistence.Table;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;
import java.util.Set;

@SupportedAnnotationTypes("com.codeslap.persistence.Table")
public class PersistenceProcessor extends AbstractProcessor {
  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment env) {
    for (TypeElement typeElement : typeElements) {
      Set<? extends Element> tables = env.getElementsAnnotatedWith(typeElement);
      for (Element table : tables) {
        PackageElement packageElement = CodeGenHelper.getPackage(table);
        boolean isClass = table.getKind() == ElementKind.CLASS;
        if (isClass) {
          try {
            String mainClassName = table.getSimpleName().toString();
            String className = mainClassName + "DataObject";
            String sourceName = packageElement.getQualifiedName() + "." + className;
            JavaFileObject sourceFile = createSourceFile(sourceName, table);

            Writer out = sourceFile.openWriter();

            Properties props = new Properties();
            URL url = this.getClass().getClassLoader().getResource("velocity.properties");
            props.load(url.openStream());

            // first, get and initialize an engine
            VelocityEngine ve = new VelocityEngine(props);
            ve.init();

            // next, get the Template
            Template t = ve.getTemplate("data_object_impl.vm");

            // create a context and add data
            VelocityContext context = new VelocityContext();
            context.put("packageName", packageElement.getSimpleName().toString());
            context.put("className", mainClassName);
            context.put("hasAutoincrement", shouldBeAutoIncrement(table));
            context.put("tableName", getTableName(table));

            // now render the template into a StringWriter
            t.merge(context, out);
            out.close();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    }
    return true;
  }

  private String getTableName(Element tableName) {
    return tableName.getAnnotation(Table.class).value();
  }

  private JavaFileObject createSourceFile(String name, Element element) throws IOException {
    return processingEnv.getFiler().createSourceFile(name, element);
  }

  private static boolean shouldBeAutoIncrement(Element type) {
    boolean autoincrement = true;
    for (Element element : type.getEnclosedElements()) {
      if (element.getKind() != ElementKind.FIELD) {
        continue;
      }

      if (element.getAnnotation(Ignore.class) != null) {
        continue;
      }

      PrimaryKey primaryKey = element.getAnnotation(PrimaryKey.class);
      if (primaryKey != null) {
        TypeMirror typeMirror = element.asType();
        TypeKind kind = typeMirror.getKind();
        if (kind == TypeKind.LONG || kind == TypeKind.INT) {
          autoincrement = primaryKey.autoincrement();
        } else {
          autoincrement = false;
        }
        break;
      }
    }
    return autoincrement;
  }
}
