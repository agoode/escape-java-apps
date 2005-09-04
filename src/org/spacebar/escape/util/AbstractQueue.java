/* AbstractQueue.java -- Implementation of some Queue methods
   Copyright (C) 2004, 2005 Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
02111-1307 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */


package org.spacebar.escape.util;

import java.util.Collection;
import java.util.NoSuchElementException;

/**
 * @author Tom Tromey (tromey@redhat.com)
 * @author Andrew John Hughes (gnu_andrew@member.fsf.org)
 * @since 1.5
 */
public abstract class AbstractQueue<E> extends AbstractCollection<E> 
  implements Queue<E>
{
  protected AbstractQueue()
  {
  }

  public boolean add(E value)
  {
    if (offer(value))
      return true;
    throw new IllegalStateException();
  }

  public boolean addAll(Collection<? extends E> c)
  {
    if (c == this)
      throw new IllegalArgumentException();
    boolean result = false;
    for (E val : c)
      {
	if (add(val))
	  result = true;
      }
    return result;
  }

  public void clear()
  {
    while (poll() != null)
      ;
  }

  public E element()
  {
    E result = peek();
    if (result == null)
      throw new NoSuchElementException();
    return result;
  }

  public E remove()
  {
    E result = poll();
    if (result == null)
      throw new NoSuchElementException();
    return result;
  }
}